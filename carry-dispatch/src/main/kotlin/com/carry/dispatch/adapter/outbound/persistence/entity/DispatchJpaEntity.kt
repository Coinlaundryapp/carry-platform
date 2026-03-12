package com.carry.dispatch.adapter.outbound.persistence.entity

import com.carry.dispatch.domain.model.Dispatch
import com.carry.dispatch.domain.vo.AssignedBy
import com.carry.dispatch.domain.vo.DispatchStatus
import com.carry.infra.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "dispatch_dispatches")
class DispatchJpaEntity(
    @Column(nullable = false, unique = true)
    val orderId: Long,

    @Column(nullable = false)
    val laundromatId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: DispatchStatus,

    var carrierId: Long?,

    @Column(nullable = false, length = 20)
    val areaCode: String,

    @Column(nullable = false)
    val desiredPickupAt: Instant,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var assignedBy: AssignedBy?,

    var assignedAt: Instant?,
    var acceptedAt: Instant?,
    var cancelReason: String?,
) : BaseEntity() {

    fun toDomain(): Dispatch = Dispatch.reconstitute(
        id = id,
        orderId = orderId,
        laundromatId = laundromatId,
        status = status,
        carrierId = carrierId,
        areaCode = areaCode,
        desiredPickupAt = desiredPickupAt,
        assignedBy = assignedBy,
        assignedAt = assignedAt,
        acceptedAt = acceptedAt,
        cancelReason = cancelReason,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun updateFrom(dispatch: Dispatch) {
        status = dispatch.status
        carrierId = dispatch.carrierId
        assignedBy = dispatch.assignedBy
        assignedAt = dispatch.assignedAt
        acceptedAt = dispatch.acceptedAt
        cancelReason = dispatch.cancelReason
    }

    companion object {
        fun fromDomain(dispatch: Dispatch): DispatchJpaEntity = DispatchJpaEntity(
            orderId = dispatch.orderId,
            laundromatId = dispatch.laundromatId,
            status = dispatch.status,
            carrierId = dispatch.carrierId,
            areaCode = dispatch.areaCode,
            desiredPickupAt = dispatch.desiredPickupAt,
            assignedBy = dispatch.assignedBy,
            assignedAt = dispatch.assignedAt,
            acceptedAt = dispatch.acceptedAt,
            cancelReason = dispatch.cancelReason,
        )
    }
}
