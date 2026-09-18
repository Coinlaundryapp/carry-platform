package com.carry.infra.kafka.dlq

import com.carry.common.metrics.MetricsPort
import com.carry.infra.kafka.KafkaConfig
import com.carry.infra.kafka.KafkaTestApplication
import io.mockk.mockk
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest(
    classes = [
        KafkaTestApplication::class,
        KafkaConfig::class,
        DlqRedriveService::class,
        DlqRedriveIntegrationTest.IntegrationConfig::class,
    ],
)
@EmbeddedKafka(
    partitions = 1,
    topics = [
        DlqRedriveIntegrationTest.TOPIC_BASIC, DlqRedriveIntegrationTest.TOPIC_BASIC_DLQ,
        DlqRedriveIntegrationTest.TOPIC_PARKED, DlqRedriveIntegrationTest.TOPIC_PARKED_DLQ,
        DlqRedriveIntegrationTest.TOPIC_BOUNDED, DlqRedriveIntegrationTest.TOPIC_BOUNDED_DLQ,
    ],
)
@TestPropertySource(
    properties = [
        "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.enable-auto-commit=false",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
    ],
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DlqRedriveIntegrationTest {

    companion object {
        const val TOPIC_BASIC = "redrive.basic"
        const val TOPIC_BASIC_DLQ = "redrive.basic.DLQ"
        const val TOPIC_PARKED = "redrive.parked"
        const val TOPIC_PARKED_DLQ = "redrive.parked.DLQ"
        const val TOPIC_BOUNDED = "redrive.bounded"
        const val TOPIC_BOUNDED_DLQ = "redrive.bounded.DLQ"
    }

    @TestConfiguration
    open class IntegrationConfig {
        @Bean open fun metrics(): MetricsPort = mockk(relaxed = true)
    }

    @Autowired lateinit var template: KafkaTemplate<String, String>
    @Autowired lateinit var redriveService: DlqRedriveService
    @Autowired lateinit var embeddedKafka: EmbeddedKafkaBroker

    private val consumers = mutableListOf<Consumer<String, String>>()

    @AfterAll
    fun tearDown() {
        consumers.forEach { runCatching { it.close() } }
    }

    private fun seedDlq(dlqTopic: String, key: String, value: String, redriveCount: Int? = null) {
        val headers = RecordHeaders().apply {
            add(RecordHeader("kafka_dlt-original-topic", dlqTopic.removeSuffix(".DLQ").toByteArray()))
            if (redriveCount != null) {
                add(RecordHeader(DlqRedriveService.REDRIVE_COUNT_HEADER, redriveCount.toString().toByteArray()))
            }
        }
        template.send(ProducerRecord(dlqTopic, null, key, value, headers)).get(5, TimeUnit.SECONDS)
    }

    private fun consumerFor(topic: String): Consumer<String, String> {
        val props = KafkaTestUtils.consumerProps("verify-${UUID.randomUUID()}", "true", embeddedKafka)
        val consumer = DefaultKafkaConsumerFactory(props, StringDeserializer(), StringDeserializer()).createConsumer()
        consumers += consumer
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic)
        return consumer
    }

    @Test
    fun `DLQ 메시지를 원본 토픽으로 재발행하고 redrive-count 헤더를 증가시키며, 재호출 시 같은 메시지를 다시 처리하지 않는다`() {
        seedDlq(TOPIC_BASIC_DLQ, "k1", "payload-1")
        seedDlq(TOPIC_BASIC_DLQ, "k2", "payload-2")

        val result = redriveService.redrive(TOPIC_BASIC, maxRecords = 10)
        assertThat(result.redriven).isEqualTo(2)
        assertThat(result.parked).isEqualTo(0)

        val records = KafkaTestUtils.getRecords(consumerFor(TOPIC_BASIC), Duration.ofSeconds(10), 2)
        val byKey = records.records(TOPIC_BASIC).associateBy { it.key() }
        assertThat(byKey.keys).containsExactlyInAnyOrder("k1", "k2")
        byKey.values.forEach { record ->
            assertThat(record.headers().lastHeader(DlqRedriveService.REDRIVE_COUNT_HEADER).value().toString(Charsets.UTF_8))
                .isEqualTo("1")
            // DeadLetterPublishingRecoverer가 붙인 진단 헤더는 보존된다
            assertThat(record.headers().lastHeader("kafka_dlt-original-topic")).isNotNull
        }

        // 오프셋이 커밋되어 같은 메시지는 다시 드레인되지 않는다
        val second = redriveService.redrive(TOPIC_BASIC, maxRecords = 10)
        assertThat(second.redriven).isEqualTo(0)
    }

    @Test
    fun `redrive-count가 한도에 달한 메시지는 재발행하지 않고 보류 처리한다 — 무한 루프 차단`() {
        seedDlq(TOPIC_PARKED_DLQ, "poison", "poison-payload", redriveCount = DlqRedriveService.MAX_REDRIVES)

        val result = redriveService.redrive(TOPIC_PARKED, maxRecords = 10)
        assertThat(result.redriven).isEqualTo(0)
        assertThat(result.parked).isEqualTo(1)

        // 원본 토픽에는 아무것도 발행되지 않는다
        val consumer = consumerFor(TOPIC_PARKED)
        val records = consumer.poll(Duration.ofSeconds(3))
        assertThat(records.count()).isEqualTo(0)
    }

    @Test
    fun `maxRecords 한도까지만 처리하고 나머지는 다음 호출에서 이어서 처리한다`() {
        seedDlq(TOPIC_BOUNDED_DLQ, "k1", "p1")
        seedDlq(TOPIC_BOUNDED_DLQ, "k2", "p2")
        seedDlq(TOPIC_BOUNDED_DLQ, "k3", "p3")

        val first = redriveService.redrive(TOPIC_BOUNDED, maxRecords = 2)
        assertThat(first.redriven).isEqualTo(2)

        val second = redriveService.redrive(TOPIC_BOUNDED, maxRecords = 10)
        assertThat(second.redriven).isEqualTo(1)
    }
}
