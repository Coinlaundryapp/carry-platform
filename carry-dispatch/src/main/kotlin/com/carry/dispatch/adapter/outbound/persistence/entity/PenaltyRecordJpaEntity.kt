package com.carry.dispatch.adapter.outbound.persistence.entity

import com.carry.dispatch.domain.model.PenaltyRecord
import com.carry.dispatch.domain.vo.PenaltyReason
import com.carry.infra.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "dispatch_penalty_records")
class PenaltyRecordJpaEntity(
    @Column(nullable = false)
    val carrierId: Long,

    @Column(nullable = false)
    val dispatchId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val reason: PenaltyReason,
) : BaseEntity() {

    fun toDomain(): PenaltyRecord = PenaltyRecord.reconstitute(
        id = id,
        carrierId = carrierId,
        dispatchId = dispatchId,
        reason = reason,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(penaltyRecord: PenaltyRecord): PenaltyRecordJpaEntity = PenaltyRecordJpaEntity(
            carrierId = penaltyRecord.carrierId,
            dispatchId = penaltyRecord.dispatchId,
            reason = penaltyRecord.reason,
        )
    }
}
