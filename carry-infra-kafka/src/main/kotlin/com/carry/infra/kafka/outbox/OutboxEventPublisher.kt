package com.carry.infra.kafka.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.trace.Span
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class OutboxEventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val INVALID_TRACE_ID = "00000000000000000000000000000000"
    }

    fun publish(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payload: Any,
        traceId: String? = null
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
                traceId = resolvedTraceId
            )
        )
    }
}
