package com.carry.operation.adapter.outbound.persistence.entity

import com.carry.operation.domain.model.OperationEvent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "operation_events")
class OperationEventJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 50)
    val eventType: String,

    @Column(nullable = false, length = 50)
    val aggregateType: String,

    @Column(nullable = false)
    val aggregateId: Long,

    @Column(nullable = false)
    val summary: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {

    fun toDomain(): OperationEvent = OperationEvent.reconstitute(
        id = id,
        eventType = eventType,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        summary = summary,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(event: OperationEvent): OperationEventJpaEntity = OperationEventJpaEntity(
            eventType = event.eventType,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            summary = event.summary,
            createdAt = event.createdAt,
        )
    }
}
