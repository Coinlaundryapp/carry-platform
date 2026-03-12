package com.carry.infra.kafka.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class OutboxEventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) {
    fun publish(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payload: Any,
        traceId: String? = null
    ) {
        outboxEventRepository.save(
            OutboxEvent(
                id = UUID.randomUUID(),
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                payload = objectMapper.writeValueAsString(payload),
                traceId = traceId
            )
        )
    }
}
