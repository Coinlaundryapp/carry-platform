package com.carry.infra.kafka

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Properties

/**
 * 멀티브로커 HA 무손실 검증.
 *
 * RF=3·min.insync.replicas=2·acks=all 토픽에 발행 → 브로커 1대 중단 → 추가 발행 + 전량 소비.
 * 단언: (1) 발행 전건이 무손실 소비, (2) 키별 순서 보존.
 *
 * 고정 호스트 포트(9092/9094/9096)로 EXTERNAL을 advertise해 Testcontainers 동적 포트
 * 재배선을 회피한다(스펙의 최대 리스크 정면 차단). docker-compose와 동일 포트이나, IT와
 * 라이브 스모크는 동시 실행하지 않으므로 충돌 없음.
 *
 * KRaft 겸임(broker+controller)은 controller 쿼럼(2/3)이 서야 broker가 "started" 로그를
 * 내므로, 컨테이너를 [Startables.deepStart]로 병렬 기동해야 데드락이 없다.
 *
 * ⚠️ Windows/WSL Docker Desktop에서 **이 테스트를 수 초 간격으로 연속 재실행**하면 직전 런의
 *    wslrelay 포트 포워딩이 미처 해제되지 않아 고정 포트 바인드가 드물게 실패할 수 있다
 *    (ContainerLaunchException). 단발 실행과 Linux CI에서는 포트가 즉시 해제되어 재현되지
 *    않는다. 연속 재실행 시 수 초 더 대기하면 해소된다(코드/설계 결함 아님).
 */
@Tag("kafka-cluster")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiBrokerNoLossIntegrationTest {

    private val network: Network = Network.newNetwork()
    private val hostPorts = listOf(9092, 9094, 9096)
    private lateinit var brokers: List<GenericContainer<*>>

    private fun broker(nodeId: Int, hostPort: Int): GenericContainer<*> {
        // addFixedExposedPort는 protected라 서브클래스 init 블록에서만 호출 가능.
        // host:hostPort → container EXTERNAL 9092 고정 매핑(동적 포트 advertised 회피).
        val c = object : GenericContainer<Nothing>(DockerImageName.parse("apache/kafka:3.8.1")) {
            init {
                addFixedExposedPort(hostPort, 9092)
            }
        }
        c.withNetwork(network)
        c.withNetworkAliases("kafka-$nodeId")
        c.withEnv(
            mapOf(
                "KAFKA_NODE_ID" to "$nodeId",
                "KAFKA_PROCESS_ROLES" to "broker,controller",
                "KAFKA_CONTROLLER_QUORUM_VOTERS" to "1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093",
                "KAFKA_LISTENERS" to "CONTROLLER://0.0.0.0:9093,INTERNAL://0.0.0.0:19092,EXTERNAL://0.0.0.0:9092",
                "KAFKA_ADVERTISED_LISTENERS" to "INTERNAL://kafka-$nodeId:19092,EXTERNAL://localhost:$hostPort",
                "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" to "CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT",
                "KAFKA_CONTROLLER_LISTENER_NAMES" to "CONTROLLER",
                "KAFKA_INTER_BROKER_LISTENER_NAME" to "INTERNAL",
                "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" to "3",
                "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR" to "3",
                "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR" to "2",
                "CLUSTER_ID" to "carry-kafka-cluster-001",
            ),
        )
        c.waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1).withStartupTimeout(Duration.ofMinutes(2)))
        return c
    }

    @BeforeAll
    fun startCluster() {
        brokers = hostPorts.mapIndexed { idx, port -> broker(idx + 1, port) }
        Startables.deepStart(brokers).join() // 쿼럼 데드락 방지: 병렬 기동
    }

    @AfterAll
    fun stopCluster() {
        brokers.forEach { it.stop() }
        network.close()
    }

    private val bootstrap get() = hostPorts.joinToString(",") { "localhost:$it" }

    private fun createHaTopic(name: String) {
        AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap)).use { admin ->
            val topic = NewTopic(name, 3, 3.toShort())
                .configs(mapOf("min.insync.replicas" to "2"))
            admin.createTopics(listOf(topic)).all().get()
        }
    }

    private fun producer() = KafkaProducer<String, String>(
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
            put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60000)
        },
    )

    private fun consumeAll(topic: String, expected: Int): List<Pair<String, String>> {
        val consumer = KafkaConsumer<String, String>(
            Properties().apply {
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
                put(ConsumerConfig.GROUP_ID_CONFIG, "no-loss-it")
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            },
        )
        consumer.subscribe(listOf(topic))
        val out = mutableListOf<Pair<String, String>>()
        val deadline = System.currentTimeMillis() + 30000
        var emptyPolls = 0
        // expected 도달 후에도 추가로 드레인해 트레일링 중복(at-least-once 재전달)까지 잡는다.
        while (System.currentTimeMillis() < deadline) {
            val records = consumer.poll(Duration.ofMillis(500))
            if (records.isEmpty) {
                if (out.size >= expected && ++emptyPolls >= 3) break
            } else {
                emptyPolls = 0
                records.forEach { out.add(it.key() to it.value()) }
            }
        }
        consumer.close()
        return out
    }

    @Test
    fun `브로커 1대 중단 후에도 발행 메시지가 무손실 소비되고 키별 순서가 보존된다`() {
        val topic = "ha.noloss.test"
        createHaTopic(topic)

        val key = "agg-1"
        val total = 200
        producer().use { p ->
            // 1차 발행
            for (i in 0 until total / 2) {
                p.send(ProducerRecord(topic, key, "msg-$i")).get()
            }
            // 브로커 1대 중단(리더 포함 가능성 유도)
            brokers[1].stop()
            // 2차 발행 — RF=3·min-ISR=2라 남은 2브로커로 무손실 지속
            for (i in total / 2 until total) {
                p.send(ProducerRecord(topic, key, "msg-$i")).get()
            }
        }

        val consumed = consumeAll(topic, total)
        // 무손실: 전건 소비
        assertThat(consumed).hasSize(total)
        // 키별 순서 보존: 같은 키라 발행 순서대로
        val values = consumed.map { it.second }
        assertThat(values).containsExactlyElementsOf((0 until total).map { "msg-$it" })
    }
}
