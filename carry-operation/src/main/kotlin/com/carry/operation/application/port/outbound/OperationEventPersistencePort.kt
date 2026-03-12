package com.carry.operation.application.port.outbound

import com.carry.operation.domain.model.OperationEvent
import java.time.Instant

interface OperationEventPersistencePort {
    fun save(event: OperationEvent): OperationEvent
    fun findRecent(limit: Int): List<OperationEvent>
    fun countByEventTypeAndCreatedAtAfter(eventType: String, after: Instant): Long
}
