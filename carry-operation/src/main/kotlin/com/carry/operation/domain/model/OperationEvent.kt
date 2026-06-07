package com.carry.operation.domain.model

import java.time.Instant

class OperationEvent private constructor(
    val id: Long?,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: Long,
    val summary: String,
    val createdAt: Instant,
) {
    companion object {
        fun create(eventType: String, aggregateType: String, aggregateId: Long, summary: String, now: Instant): OperationEvent {
            return OperationEvent(
                id = null,
                eventType = eventType,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                summary = summary,
                createdAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            eventType: String,
            aggregateType: String,
            aggregateId: Long,
            summary: String,
            createdAt: Instant,
        ): OperationEvent = OperationEvent(
            id, eventType, aggregateType, aggregateId, summary, createdAt,
        )
    }
}
