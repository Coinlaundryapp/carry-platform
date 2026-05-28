package com.carry.infra.kafka

import com.carry.common.metrics.MetricsPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.DeserializationException

class KafkaConfigTest {

    private val metrics = mockk<MetricsPort>(relaxed = true)
    private val config = KafkaConfig()

    @Test
    fun `dlqDestinationResolver는 원본 토픽에 _DLQ 접미사를 붙이고 파티션을 유지한다`() {
        val record = ConsumerRecord<Any, Any>("carry.Order.events", 2, 0L, null, null)

        val target = KafkaConfig.dlqDestinationResolver(record, RuntimeException("boom"))

        assertThat(target.topic()).isEqualTo("carry.Order.events.DLQ")
        assertThat(target.partition()).isEqualTo(2)
    }

    @Test
    fun `재시도 정책 상수 — 1초 간격으로 최대 3회 재시도`() {
        assertThat(KafkaConfig.BACKOFF_INTERVAL_MS).isEqualTo(1000L)
        assertThat(KafkaConfig.MAX_RETRIES).isEqualTo(3L)
    }

    @Test
    fun `재시도 불가 예외 목록에 직렬화 실패와 잘못된 인자 예외가 포함된다`() {
        assertThat(KafkaConfig.NON_RETRYABLE_EXCEPTIONS).contains(
            DeserializationException::class.java,
            IllegalArgumentException::class.java,
        )
    }

    @Test
    fun `wrapWithMetrics는 delegate 위임 후 kafka_dlq_count 메트릭을 토픽과 예외 태그로 증가시킨다`() {
        val delegate = mockk<ConsumerRecordRecoverer>(relaxed = true)
        val wrapped = KafkaConfig.wrapWithMetrics(delegate, metrics)
        val record = ConsumerRecord<Any, Any>("carry.Order.events", 0, 0L, "key", "value")
        val ex = IllegalStateException("boom")

        wrapped.accept(record, ex)

        verifyOrder {
            delegate.accept(record, ex)
            metrics.incrementCounter(
                "kafka.dlq.count",
                "topic" to "carry.Order.events",
                "exception" to "IllegalStateException",
            )
        }
    }

    @Test
    fun `ContainerCustomizer는 stopContainerWhenFenced를 true로 설정해 graceful shutdown 보장`() {
        val container = mockk<ConcurrentMessageListenerContainer<String, String>>(relaxed = true)
        val props = ContainerProperties("test-topic")
        every { container.containerProperties } returns props

        val customizer = config.kafkaListenerContainerCustomizer()
        customizer.configure(container)

        assertThat(props.isStopContainerWhenFenced).isTrue()
    }

    @Test
    fun `wrapWithMetrics는 cause가 있으면 cause 클래스명을 메트릭 태그로 사용한다 — Spring Kafka의 ListenerExecutionFailedException 래핑 대응`() {
        val delegate = mockk<ConsumerRecordRecoverer>(relaxed = true)
        val wrapped = KafkaConfig.wrapWithMetrics(delegate, metrics)
        val record = ConsumerRecord<Any, Any>("carry.Order.events", 0, 0L, "key", "value")
        val rootCause = IllegalStateException("real failure")
        val wrappedException = RuntimeException("Listener execution failed", rootCause)

        wrapped.accept(record, wrappedException)

        verifyOrder {
            delegate.accept(record, wrappedException)
            metrics.incrementCounter(
                "kafka.dlq.count",
                "topic" to "carry.Order.events",
                "exception" to "IllegalStateException",
            )
        }
    }
}
