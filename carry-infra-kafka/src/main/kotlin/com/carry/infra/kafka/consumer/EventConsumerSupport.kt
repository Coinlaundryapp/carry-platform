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

    /**
     * 이벤트를 **소비자 그룹별로** 한 번만 처리한다. [consumerGroup]은 호출 컨슈머의
     * `@KafkaListener(groupId=...)`와 일치해야 한다 — 같은 이벤트가 여러 그룹으로 fan-out돼도
     * 각 그룹이 독립적으로 1회 처리하도록 키에 그룹을 포함한다.
     */
    @Transactional
    fun processIfNotDuplicate(
        consumerGroup: String,
        eventId: String,
        traceId: String? = null,
        eventType: String? = null,
        block: () -> Unit,
    ) {
        // claim-first: 처리 시작 전에 원자적으로 선점한다. 0행이면 이 그룹이 이미 처리함(중복) → skip.
        // 동시 중복에서도 ON CONFLICT가 한 트랜잭션만 통과시켜 block은 최대 1회 실행된다.
        if (processedEventRepository.claim(consumerGroup, eventId, Instant.now()) == 0) {
            log.debug("Skipping duplicate event: group={} id={}", consumerGroup, eventId)
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
