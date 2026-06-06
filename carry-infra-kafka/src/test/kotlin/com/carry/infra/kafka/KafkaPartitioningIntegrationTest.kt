package com.carry.infra.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.stereotype.Component
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * 키(aggregate_id) 기반 파티셔닝의 두 핵심 성질을 6 파티션 임베디드 브로커로 검증한다:
 *   1) 같은 키 → 같은 파티션 + 발행 순서 보존 (saga 순서 의존 보장).
 *   2) 다른 키 → 파티션 분산 + `concurrency: 3` 다중 스레드 동시 소비 (처리량 천장 제거).
 *
 * 실제 이벤트 토픽은 Debezium이 6 파티션으로 생성한다(register-connector.json topic.creation).
 * 여기서는 동일한 6 파티션 토픽에 동일한 StringSerializer 키로 발행해 분배 동작을 재현한다.
 */
@SpringBootTest(
    classes = [
        KafkaTestApplication::class,
        KafkaPartitioningIntegrationTest.RecordingConfig::class,
    ],
)
@EmbeddedKafka(
    partitions = 6,
    topics = [KafkaPartitioningIntegrationTest.TOPIC],
)
@TestPropertySource(
    properties = [
        "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=kafka-partitioning-it",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.listener.concurrency=3",
        "spring.kafka.listener.missing-topics-fatal=false",
    ],
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaPartitioningIntegrationTest {

    companion object {
        const val TOPIC = "carry.partitioning.test"
        private const val KEY_COUNT = 6
        private const val PER_KEY = 10
        private const val TOTAL = KEY_COUNT * PER_KEY
    }

    data class Received(val partition: Int, val key: String, val value: Int, val thread: String)

    @TestConfiguration
    open class RecordingConfig {
        @Bean open fun recordingListener(): RecordingListener = RecordingListener()
    }

    @Component
    open class RecordingListener {
        val records = CopyOnWriteArrayList<Received>()

        @KafkaListener(topics = [TOPIC], groupId = "kafka-partitioning-it")
        fun consume(record: ConsumerRecord<String, String>) {
            records += Received(
                partition = record.partition(),
                key = record.key(),
                value = record.value().toInt(),
                thread = Thread.currentThread().name,
            )
        }
    }

    @Autowired lateinit var template: KafkaTemplate<String, String>
    @Autowired lateinit var listener: RecordingListener

    @Test
    fun `같은 키는 단일 파티션에 순서대로, 다른 키는 파티션 분산되어 동시 소비된다`() {
        // 6개 키 × 10개 값을 키별로 순차 발행. 키마다 값 0..9가 발행 순서.
        for (k in 0 until KEY_COUNT) {
            for (v in 0 until PER_KEY) {
                template.send(TOPIC, "agg-$k", v.toString())
            }
        }
        template.flush()

        await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            assertThat(listener.records).hasSize(TOTAL)
        }

        val byKey = listener.records.groupBy { it.key }

        // 1) 같은 키 → 단일 파티션 + 발행 순서(0..9) 보존
        byKey.forEach { (key, recs) ->
            val partitions = recs.map { it.partition }.toSet()
            assertThat(partitions)
                .describedAs("키 %s는 단일 파티션이어야 한다", key)
                .hasSize(1)
            assertThat(recs.map { it.value })
                .describedAs("키 %s는 발행 순서가 보존되어야 한다", key)
                .containsExactlyElementsOf(0 until PER_KEY)
        }

        // 2) 다른 키 → 2개 이상의 파티션에 분산 (단일 파티션 천장 제거)
        val usedPartitions = listener.records.map { it.partition }.toSet()
        assertThat(usedPartitions)
            .describedAs("서로 다른 키가 여러 파티션에 분산되어야 한다")
            .hasSizeGreaterThanOrEqualTo(2)

        // 3) concurrency=3 → 2개 이상 컨슈머 스레드가 동시 소비
        val usedThreads = listener.records.map { it.thread }.toSet()
        assertThat(usedThreads)
            .describedAs("여러 컨슈머 스레드가 병렬 소비해야 한다 (concurrency=3)")
            .hasSizeGreaterThanOrEqualTo(2)
    }
}
