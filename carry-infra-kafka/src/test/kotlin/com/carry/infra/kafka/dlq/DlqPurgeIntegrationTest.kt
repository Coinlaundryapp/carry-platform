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
        DlqPurgeService::class,
        DlqPurgeIntegrationTest.IntegrationConfig::class,
    ],
)
@EmbeddedKafka(
    partitions = 1,
    topics = [
        DlqPurgeIntegrationTest.TOPIC_BASIC, DlqPurgeIntegrationTest.TOPIC_BASIC_DLQ,
        DlqPurgeIntegrationTest.TOPIC_BOUNDED, DlqPurgeIntegrationTest.TOPIC_BOUNDED_DLQ,
        DlqPurgeIntegrationTest.TOPIC_UNPROCESSED, DlqPurgeIntegrationTest.TOPIC_UNPROCESSED_DLQ,
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
class DlqPurgeIntegrationTest {

    companion object {
        const val TOPIC_BASIC = "purge.basic"
        const val TOPIC_BASIC_DLQ = "purge.basic.DLQ"
        const val TOPIC_BOUNDED = "purge.bounded"
        const val TOPIC_BOUNDED_DLQ = "purge.bounded.DLQ"
        const val TOPIC_UNPROCESSED = "purge.unprocessed"
        const val TOPIC_UNPROCESSED_DLQ = "purge.unprocessed.DLQ"
    }

    @TestConfiguration
    open class IntegrationConfig {
        @Bean open fun metrics(): MetricsPort = mockk(relaxed = true)
    }

    @Autowired lateinit var template: KafkaTemplate<String, String>
    @Autowired lateinit var redriveService: DlqRedriveService
    @Autowired lateinit var purgeService: DlqPurgeService
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

    /** earliest로 새 컨슈머를 붙여 DLQ에 물리적으로 남은 레코드 수를 센다(deleteRecords로 low watermark가 전진하면 줄어듦). */
    private fun remainingInDlq(dlqTopic: String, expectAtMost: Int): Int {
        val props = KafkaTestUtils.consumerProps("verify-${UUID.randomUUID()}", "true", embeddedKafka)
        val consumer = DefaultKafkaConsumerFactory(props, StringDeserializer(), StringDeserializer()).createConsumer()
        consumers += consumer
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, dlqTopic)
        var count = 0
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadline && count < expectAtMost) {
            count += consumer.poll(Duration.ofMillis(500)).count()
        }
        return count
    }

    @Test
    fun `redrive 후 purge하면 committed offset까지 물리 절단해 잔류(redriven copy + parked)를 회수한다`() {
        seedDlq(TOPIC_BASIC_DLQ, "recoverable", "p1") // redrive 대상
        seedDlq(TOPIC_BASIC_DLQ, "poison", "p2", redriveCount = DlqRedriveService.MAX_REDRIVES) // parked 대상

        val redrive = redriveService.redrive(TOPIC_BASIC, maxRecords = 10)
        assertThat(redrive.redriven).isEqualTo(1)
        assertThat(redrive.parked).isEqualTo(1)
        // redrive는 redriven·parked 모두 commit → committed offset = 2. 둘 다 DLQ에 물리 잔류.
        assertThat(remainingInDlq(TOPIC_BASIC_DLQ, expectAtMost = 5)).isEqualTo(2)

        val result = purgeService.purge(TOPIC_BASIC)
        assertThat(result.purged).isEqualTo(2)
        assertThat(remainingInDlq(TOPIC_BASIC_DLQ, expectAtMost = 5)).isEqualTo(0)
    }

    @Test
    fun `committed offset 이상(redrive 미처리분)은 purge가 보존한다`() {
        seedDlq(TOPIC_BOUNDED_DLQ, "k1", "p1")
        seedDlq(TOPIC_BOUNDED_DLQ, "k2", "p2")
        seedDlq(TOPIC_BOUNDED_DLQ, "k3", "p3")

        // maxRecords=2 → 앞 2건만 처리·commit(committed offset = 2), 3번째는 미처리로 남는다.
        redriveService.redrive(TOPIC_BOUNDED, maxRecords = 2)

        val result = purgeService.purge(TOPIC_BOUNDED)
        assertThat(result.purged).isEqualTo(2)
        // 미처리 1건은 물리 보존.
        assertThat(remainingInDlq(TOPIC_BOUNDED_DLQ, expectAtMost = 5)).isEqualTo(1)
    }

    @Test
    fun `redrive가 한 번도 돌지 않은 토픽은 purge가 아무것도 삭제하지 않는다`() {
        seedDlq(TOPIC_UNPROCESSED_DLQ, "k1", "p1")
        seedDlq(TOPIC_UNPROCESSED_DLQ, "k2", "p2")

        val result = purgeService.purge(TOPIC_UNPROCESSED)
        assertThat(result.purged).isEqualTo(0)
        assertThat(remainingInDlq(TOPIC_UNPROCESSED_DLQ, expectAtMost = 5)).isEqualTo(2)
    }
}
