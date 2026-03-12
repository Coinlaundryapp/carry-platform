package com.carry.operation.adapter.outbound.persistence.repository

import com.carry.operation.adapter.outbound.persistence.entity.OperationEventJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface OperationEventJpaRepository : JpaRepository<OperationEventJpaEntity, Long> {
    fun findTop50ByOrderByCreatedAtDesc(): List<OperationEventJpaEntity>
    fun countByEventTypeAndCreatedAtAfter(eventType: String, after: Instant): Long
}
