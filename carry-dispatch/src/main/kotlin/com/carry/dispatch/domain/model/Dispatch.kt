package com.carry.dispatch.domain.model

import com.carry.dispatch.domain.exception.DispatchAlreadyAcceptedException
import com.carry.dispatch.domain.exception.DispatchNotCancellableException
import com.carry.dispatch.domain.exception.DispatchNotPendingException
import com.carry.dispatch.domain.exception.DispatchTimeoutNotAllowedException
import com.carry.dispatch.domain.vo.AssignedBy
import com.carry.dispatch.domain.vo.DispatchStatus
import com.carry.dispatch.domain.vo.PenaltyReason
import java.time.Instant
import java.time.temporal.ChronoUnit

class Dispatch private constructor(
    val id: Long?,
    val orderId: Long,
    val laundromatId: Long,
    private var _status: DispatchStatus,
    private var _carrierId: Long?,
    val areaCode: String,
    val desiredPickupAt: Instant,
    private var _assignedBy: AssignedBy?,
    private var _assignedAt: Instant?,
    private var _acceptedAt: Instant?,
    private var _cancelReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val status get() = _status
    val carrierId get() = _carrierId
    val assignedBy get() = _assignedBy
    val assignedAt get() = _assignedAt
    val acceptedAt get() = _acceptedAt
    val cancelReason get() = _cancelReason

    companion object {
        fun create(orderId: Long, laundromatId: Long, areaCode: String, desiredPickupAt: Instant): Dispatch {
            val now = Instant.now()
            return Dispatch(
                id = null, orderId = orderId, laundromatId = laundromatId,
                _status = DispatchStatus.PENDING, _carrierId = null,
                areaCode = areaCode, desiredPickupAt = desiredPickupAt,
                _assignedBy = null, _assignedAt = null, _acceptedAt = null,
                _cancelReason = null, createdAt = now, updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long, orderId: Long, laundromatId: Long, status: DispatchStatus,
            carrierId: Long?, areaCode: String, desiredPickupAt: Instant,
            assignedBy: AssignedBy?, assignedAt: Instant?, acceptedAt: Instant?,
            cancelReason: String?, createdAt: Instant, updatedAt: Instant,
        ): Dispatch = Dispatch(
            id, orderId, laundromatId, status, carrierId, areaCode, desiredPickupAt,
            assignedBy, assignedAt, acceptedAt, cancelReason, createdAt, updatedAt,
        )
    }

    fun claimByCarrier(carrierId: Long) {
        if (_status != DispatchStatus.PENDING) throw DispatchNotPendingException()
        _status = DispatchStatus.ACCEPTED
        _carrierId = carrierId
        _assignedBy = AssignedBy.CARRIER
        _acceptedAt = Instant.now()
    }

    fun assignByCoordinator(carrierId: Long) {
        if (_status != DispatchStatus.PENDING) throw DispatchNotPendingException()
        _status = DispatchStatus.ASSIGNED
        _carrierId = carrierId
        _assignedBy = AssignedBy.COORDINATOR
        _assignedAt = Instant.now()
    }

    fun acceptAssignment() {
        if (_status != DispatchStatus.ASSIGNED) throw DispatchAlreadyAcceptedException()
        _status = DispatchStatus.ACCEPTED
        _acceptedAt = Instant.now()
    }

    fun rejectAssignment(): PenaltyRecord {
        if (_status != DispatchStatus.ASSIGNED) throw DispatchAlreadyAcceptedException()
        val penalizedCarrierId = _carrierId!!
        _status = DispatchStatus.PENDING
        _carrierId = null
        _assignedBy = null
        _assignedAt = null
        return PenaltyRecord.create(penalizedCarrierId, id!!, PenaltyReason.REJECTED_FORCED_ASSIGNMENT)
    }

    fun cancel(reason: String) {
        if (!_status.canTransitionTo(DispatchStatus.CANCELLED)) {
            throw DispatchNotCancellableException(_status)
        }
        _status = DispatchStatus.CANCELLED
        _cancelReason = reason
    }

    fun timeout() {
        if (_status != DispatchStatus.PENDING) throw DispatchTimeoutNotAllowedException(_status)
        _status = DispatchStatus.TIMEOUT
    }

    fun isExpired(): Boolean =
        _status == DispatchStatus.PENDING &&
            Instant.now().isAfter(desiredPickupAt.minus(30, ChronoUnit.MINUTES))
}
