package com.carry.infra.kafka

import com.carry.common.metrics.MetricsPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.util.backoff.FixedBackOff

/**
 * Kafka Consumer 에러 핸들링 정책:
 *   1) 일시적 실패는 [BACKOFF_INTERVAL_MS] 간격으로 최대 [MAX_RETRIES]회 재시도.
 *   2) 모든 재시도 소진 시 원본 토픽에 `.DLQ` 접미사를 붙인 토픽으로 발행하고
 *      `kafka.dlq.count` 메트릭을 증가시킨다.
 *   3) [NON_RETRYABLE_EXCEPTIONS]에 등록된 영구 실패(예: 직렬화 오류)는 즉시 DLQ로.
 *
 * `DeadLetterPublishingRecoverer`가 원본 토픽·파티션·오프셋·예외 메시지 등을
 * `kafka_dlt-*` 헤더로 자동 첨부한다.
 */
@Configuration
@EnableKafka
class KafkaConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val DLQ_SUFFIX = ".DLQ"
        const val BACKOFF_INTERVAL_MS = 1000L
        const val MAX_RETRIES = 3L

        val NON_RETRYABLE_EXCEPTIONS: List<Class<out Exception>> = listOf(
            DeserializationException::class.java,
            IllegalArgumentException::class.java,
        )

        /** 원본 record를 동일 파티션의 `<원본토픽>.DLQ`로 라우팅한다. */
        fun dlqDestinationResolver(record: ConsumerRecord<*, *>, @Suppress("UNUSED_PARAMETER") exception: Exception): TopicPartition =
            TopicPartition(record.topic() + DLQ_SUFFIX, record.partition())

        /** DLQ 발행 직후 `kafka.dlq.count` 메트릭을 증가시키는 데코레이터. */
        fun wrapWithMetrics(delegate: ConsumerRecordRecoverer, metrics: MetricsPort): ConsumerRecordRecoverer =
            ConsumerRecordRecoverer { record, ex ->
                delegate.accept(record, ex)
                metrics.incrementCounter(
                    "kafka.dlq.count",
                    "topic" to record.topic(),
                    "exception" to ex.javaClass.simpleName,
                )
            }
    }

    @Bean
    fun kafkaListenerErrorHandler(
        kafkaTemplate: KafkaTemplate<String, String>,
        metrics: MetricsPort,
    ): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate, ::dlqDestinationResolver)
        val handler = DefaultErrorHandler(
            wrapWithMetrics(recoverer, metrics),
            FixedBackOff(BACKOFF_INTERVAL_MS, MAX_RETRIES),
        )
        NON_RETRYABLE_EXCEPTIONS.forEach { handler.addNotRetryableExceptions(it) }
        handler.setRetryListeners({ record, ex, deliveryAttempt ->
            log.warn(
                "Kafka retry attempt={} topic={} offset={} cause={}",
                deliveryAttempt, record.topic(), record.offset(), ex.message,
            )
        })
        return handler
    }
}
