package com.carry.infra.kafka.consumer

import io.micrometer.tracing.Tracer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class EventConsumerSupport(
    private val processedEventRepository: ProcessedEventRepository,
    private val tracer: Tracer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processIfNotDuplicate(eventId: String, traceId: String? = null, eventType: String? = null, block: () -> Unit) {
        // claim-first: 처리 시작 전에 원자적으로 선점한다. 0행이면 이미 처리됨(중복) → skip.
        // 동시 중복에서도 ON CONFLICT가 한 트랜잭션만 통과시켜 block은 최대 1회 실행된다.
        if (processedEventRepository.claim(eventId, Instant.now()) == 0) {
            log.debug("Skipping duplicate event: {}", eventId)
            return
        }

        val span = tracer.nextSpan().name("consume.${eventType ?: "unknown"}")
        traceId?.let { span.tag("saga.traceId", it) }
        span.start()
        try {
            tracer.withSpan(span).use {
                traceId?.let { MDC.put("saga.traceId", it) }
                block()
            }
        } catch (e: Exception) {
            // block 실패 → 예외 전파 → @Transactional 롤백 → claim 행도 롤백 → 재처리 가능(at-least-once)
            span.error(e)
            throw e
        } finally {
            MDC.remove("saga.traceId")
            span.end()
        }
    }
}
