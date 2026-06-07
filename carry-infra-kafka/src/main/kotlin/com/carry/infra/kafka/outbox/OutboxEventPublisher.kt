package com.carry.infra.kafka.outbox

import com.carry.event.port.EventPublisherPort
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.trace.Span
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * [EventPublisherPort]의 Outbox 기반 구현체.
 *
 * 도메인은 [EventPublisherPort]에만 의존하며, 본 클래스는 carry-infra-kafka 어셈블리에 격리된다.
 */
@Component
class OutboxEventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
) : EventPublisherPort {
    companion object {
        private const val INVALID_TRACE_ID = "00000000000000000000000000000000"
    }

    override fun publish(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payload: Any,
        traceId: String?,
    ) {
        val resolvedTraceId = traceId
            ?: Span.current().spanContext.traceId.takeIf { it != INVALID_TRACE_ID }

        outboxEventRepository.save(
            OutboxEvent(
                id = UUID.randomUUID(),
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                payload = objectMapper.writeValueAsString(payload),
                traceId = resolvedTraceId,
            ),
        )
    }
}
