package com.carry.infra.kafka

import com.carry.common.metrics.MetricsPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ContainerCustomizer
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.util.backoff.FixedBackOff

/**
 * Kafka Consumer 에러 핸들링 정책:
 *   1) 일시적 실패는 [BACKOFF_INTERVAL_MS] 간격으로 최대 [MAX_RETRIES]회 재시도.
 *   2) 모든 재시도 소진 시 원본 토픽에 `.DLQ` 접미사를 붙인 토픽으로 발행하고
 *      `carry.kafka.dlq` 메트릭을 증가시킨다.
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

        /**
         * DLQ 발행 직후 `carry.kafka.dlq` 메트릭을 증가시키는 데코레이터.
         *
         * Spring Kafka가 사용자 예외를 `ListenerExecutionFailedException`으로 wrapping해
         * 핸들러에 전달하므로, 메트릭 태그는 원인 예외(cause)의 클래스명을 사용한다.
         * cause가 없으면 전달된 예외 자체를 사용한다.
         */
        fun wrapWithMetrics(delegate: ConsumerRecordRecoverer, metrics: MetricsPort): ConsumerRecordRecoverer =
            ConsumerRecordRecoverer { record, ex ->
                delegate.accept(record, ex)
                val rootCause = ex.cause ?: ex
                metrics.incrementCounter(
                    "carry.kafka.dlq",
                    "topic" to record.topic(),
                    "exception" to rootCause.javaClass.simpleName,
                )
            }

        /**
         * 컨텍스트가 재시작(Lifecycle stop → start)돼도 리스너의 autoStartup 을 지키게 한다.
         *
         * 레지스트리 기본값(alwaysStartAfterRefresh=true)은 refresh 이후의 start() 에서 autoStartup 을 무시하고
         * 컨테이너를 전부 시작한다. Spring Framework 7 의 테스트 컨텍스트 캐시는 쓰지 않는 컨텍스트를 일시정지했다가
         * 재사용할 때 restart 하므로, 브로커가 없는 테스트 프로필(auto-startup=false)에서 컨슈머 수십 개가 매번
         * 떴다 닫혔다 — 그 churn 이 JDK 21 가상 스레드 pinning 교착으로 4코어 CI 를 멈췄다.
         * 운영 리스너는 모두 autoStartup=true 라 동작이 바뀌지 않는다. static 이라 이 설정 클래스를 앞당겨
         * 초기화하지 않는다(BeanPostProcessor 규약).
         */
        @JvmStatic
        @Bean
        fun honorListenerAutoStartupOnRestart(): BeanPostProcessor = object : BeanPostProcessor {
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                if (bean is KafkaListenerEndpointRegistry) bean.setAlwaysStartAfterRefresh(false)
                return bean
            }
        }
    }

    /**
     * Graceful shutdown 시 fenced(즉, 리밸런싱·종료 신호로 더 이상 메시지 처리 권한이
     * 없어진) 컨테이너를 즉시 멈추도록 설정한다. 이미 처리 중이던 record는 마무리하고
     * 다음 poll에서 깨끗하게 빠져나가, 부분 처리로 인한 중복·유실 위험을 줄인다.
     */
    @Bean
    fun kafkaListenerContainerCustomizer(): ContainerCustomizer<String, String, ConcurrentMessageListenerContainer<String, String>> =
        ContainerCustomizer { container ->
            container.containerProperties.isStopContainerWhenFenced = true
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
                deliveryAttempt, record.topic(), record.offset(), ex?.message,
            )
        })
        return handler
    }
}
