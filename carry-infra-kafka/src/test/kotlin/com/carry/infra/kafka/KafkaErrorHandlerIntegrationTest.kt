package com.carry.infra.kafka

import com.carry.common.metrics.MetricsPort
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.stereotype.Component
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(
    classes = [
        KafkaTestApplication::class,
        KafkaConfig::class,
        KafkaErrorHandlerIntegrationTest.IntegrationConfig::class,
    ],
)
@EmbeddedKafka(
    partitions = 1,
    topics = [
        KafkaErrorHandlerIntegrationTest.TOPIC_RETRYABLE,
        KafkaErrorHandlerIntegrationTest.TOPIC_RETRYABLE_DLQ,
        KafkaErrorHandlerIntegrationTest.TOPIC_NON_RETRYABLE,
        KafkaErrorHandlerIntegrationTest.TOPIC_NON_RETRYABLE_DLQ,
    ],
)
@TestPropertySource(
    properties = [
        "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=kafka-error-handler-it",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.listener.missing-topics-fatal=false",
    ],
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaErrorHandlerIntegrationTest {

    companion object {
        const val TOPIC_RETRYABLE = "test.retryable"
        const val TOPIC_RETRYABLE_DLQ = "test.retryable.DLQ"
        const val TOPIC_NON_RETRYABLE = "test.nonretryable"
        const val TOPIC_NON_RETRYABLE_DLQ = "test.nonretryable.DLQ"
    }

    @TestConfiguration
    open class IntegrationConfig {
        @Bean open fun metrics(): MetricsPort = mockk(relaxed = true)

        @Bean open fun failingListener(): FailingListener = FailingListener()
    }

    @Component
    open class FailingListener {
        val retryableAttempts = AtomicInteger(0)
        val nonRetryableAttempts = AtomicInteger(0)

        @KafkaListener(topics = [TOPIC_RETRYABLE], groupId = "it-retryable")
        fun consumeRetryable(record: ConsumerRecord<String, String>) {
            retryableAttempts.incrementAndGet()
            throw IllegalStateException("forced retryable failure: ${record.value()}")
        }

        @KafkaListener(topics = [TOPIC_NON_RETRYABLE], groupId = "it-nonretryable")
        fun consumeNonRetryable(record: ConsumerRecord<String, String>) {
            nonRetryableAttempts.incrementAndGet()
            throw IllegalArgumentException("forced non-retryable failure: ${record.value()}")
        }
    }

    @Autowired lateinit var template: KafkaTemplate<String, String>
    @Autowired lateinit var listener: FailingListener
    @Autowired lateinit var metrics: MetricsPort
    @Autowired lateinit var embeddedKafka: EmbeddedKafkaBroker

    private val dlqConsumers = mutableListOf<Consumer<String, String>>()

    @AfterAll
    fun tearDown() {
        dlqConsumers.forEach { runCatching { it.close() } }
    }

    @Test
    fun `재시도 가능 예외 — 1초 간격 3회 재시도 후 DLQ 토픽으로 전송된다`() {
        template.send(TOPIC_RETRYABLE, "k1", "payload-1").get(5, TimeUnit.SECONDS)

        // FixedBackOff(1초, 3회) → 원본 1회 + 재시도 3회 = 총 4회 시도
        await().atMost(20, TimeUnit.SECONDS).untilAsserted {
            assertThat(listener.retryableAttempts.get()).isEqualTo(4)
        }

        val dlqRecord = consumeOneFrom(TOPIC_RETRYABLE_DLQ)
        assertThat(dlqRecord.value()).isEqualTo("payload-1")
        assertThat(headerValue(dlqRecord, "kafka_dlt-original-topic")).isEqualTo(TOPIC_RETRYABLE)
        // Spring Kafka는 사용자 예외를 ListenerExecutionFailedException으로 wrapping하므로
        // 원인 예외 정보는 stacktrace 또는 message 헤더에서 확인한다.
        assertThat(headerValue(dlqRecord, "kafka_dlt-exception-stacktrace"))
            .contains("IllegalStateException", "forced retryable failure")

        verify {
            metrics.incrementCounter(
                "carry.kafka.dlq",
                "topic" to TOPIC_RETRYABLE,
                "exception" to "IllegalStateException",
            )
        }
    }

    @Test
    fun `재시도 불가 예외 — 재시도 없이 1회 시도 후 즉시 DLQ로 전송된다`() {
        template.send(TOPIC_NON_RETRYABLE, "k2", "payload-2").get(5, TimeUnit.SECONDS)

        // notRetryable 분류된 예외는 원본 시도 1회만
        await().atMost(15, TimeUnit.SECONDS).untilAsserted {
            assertThat(listener.nonRetryableAttempts.get()).isEqualTo(1)
        }

        val dlqRecord = consumeOneFrom(TOPIC_NON_RETRYABLE_DLQ)
        assertThat(dlqRecord.value()).isEqualTo("payload-2")
        assertThat(headerValue(dlqRecord, "kafka_dlt-original-topic")).isEqualTo(TOPIC_NON_RETRYABLE)
        assertThat(headerValue(dlqRecord, "kafka_dlt-exception-stacktrace"))
            .contains("IllegalArgumentException", "forced non-retryable failure")

        verify {
            metrics.incrementCounter(
                "carry.kafka.dlq",
                "topic" to TOPIC_NON_RETRYABLE,
                "exception" to "IllegalArgumentException",
            )
        }
    }

    private fun consumeOneFrom(topic: String): ConsumerRecord<String, String> {
        val props = KafkaTestUtils.consumerProps("dlq-${UUID.randomUUID()}", "true", embeddedKafka)
        val consumer = DefaultKafkaConsumerFactory(props, StringDeserializer(), StringDeserializer()).createConsumer()
        dlqConsumers += consumer
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic)
        return KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(15))
    }

    private fun headerValue(record: ConsumerRecord<String, String>, name: String): String? =
        record.headers().lastHeader(name)?.value()?.toString(Charsets.UTF_8)
}
