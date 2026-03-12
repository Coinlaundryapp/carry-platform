package com.carry.operation.adapter.outbound.persistence

import com.carry.operation.adapter.outbound.persistence.entity.OperationEventJpaEntity
import com.carry.operation.adapter.outbound.persistence.repository.OperationEventJpaRepository
import com.carry.operation.application.port.outbound.OperationEventPersistencePort
import com.carry.operation.domain.model.OperationEvent
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class OperationEventPersistenceAdapter(
    private val operationEventJpaRepository: OperationEventJpaRepository,
) : OperationEventPersistencePort {

    override fun save(event: OperationEvent): OperationEvent {
        val entity = OperationEventJpaEntity.fromDomain(event)
        return operationEventJpaRepository.save(entity).toDomain()
    }

    override fun findRecent(limit: Int): List<OperationEvent> {
        return operationEventJpaRepository.findTop50ByOrderByCreatedAtDesc()
            .map { it.toDomain() }
    }

    override fun countByEventTypeAndCreatedAtAfter(eventType: String, after: Instant): Long {
        return operationEventJpaRepository.countByEventTypeAndCreatedAtAfter(eventType, after)
    }
}
