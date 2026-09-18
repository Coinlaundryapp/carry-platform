package com.carry.dispatch.domain.model

import com.carry.dispatch.domain.exception.DispatchAlreadyAcceptedException
import com.carry.dispatch.domain.exception.DispatchNotCancellableException
import com.carry.dispatch.domain.exception.DispatchNotPendingException
import com.carry.dispatch.domain.exception.DispatchTimeoutNotAllowedException
import com.carry.dispatch.domain.vo.AssignedBy
import com.carry.dispatch.domain.vo.DispatchStatus
import com.carry.dispatch.domain.vo.PenaltyReason
import java.time.Duration
import java.time.Instant

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
        fun create(orderId: Long, laundromatId: Long, areaCode: String, desiredPickupAt: Instant, now: Instant): Dispatch {
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

    /**
     * 캐리어 본인이 PENDING 배차를 직접 선점한다. **멱등**: 이미 이 캐리어가 잡아 ACCEPTED면
     * no-op(false), 실제 전이면 true. 다른 캐리어 소유/타 상태면 충돌로 예외.
     */
    fun claimByCarrier(carrierId: Long, now: Instant): Boolean {
        if (_status == DispatchStatus.ACCEPTED && _carrierId == carrierId && _assignedBy == AssignedBy.CARRIER) {
            return false
        }
        if (_status != DispatchStatus.PENDING) throw DispatchNotPendingException()
        _status = DispatchStatus.ACCEPTED
        _carrierId = carrierId
        _assignedBy = AssignedBy.CARRIER
        _acceptedAt = now
        return true
    }

    /**
     * 코디네이터가 PENDING 배차를 캐리어에게 지정한다. **멱등**: 이미 같은 캐리어로 ASSIGNED면
     * no-op(false), 실제 전이면 true. 다른 캐리어 지정/타 상태면 충돌로 예외.
     */
    fun assignByCoordinator(carrierId: Long, now: Instant): Boolean {
        if (_status == DispatchStatus.ASSIGNED && _carrierId == carrierId) return false
        if (_status != DispatchStatus.PENDING) throw DispatchNotPendingException()
        _status = DispatchStatus.ASSIGNED
        _carrierId = carrierId
        _assignedBy = AssignedBy.COORDINATOR
        _assignedAt = now
        return true
    }

    /**
     * 지정된 배차를 캐리어가 수락한다(소유 검증은 서비스). **멱등**: 이미 ACCEPTED면 no-op(false),
     * 실제 전이면 true. ASSIGNED·ACCEPTED 외 상태면 충돌로 예외.
     */
    fun acceptAssignment(now: Instant): Boolean {
        if (_status == DispatchStatus.ACCEPTED) return false
        if (_status != DispatchStatus.ASSIGNED) throw DispatchAlreadyAcceptedException()
        _status = DispatchStatus.ACCEPTED
        _acceptedAt = now
        return true
    }

    fun rejectAssignment(now: Instant): PenaltyRecord {
        if (_status != DispatchStatus.ASSIGNED) throw DispatchAlreadyAcceptedException()
        val penalizedCarrierId = _carrierId!!
        _status = DispatchStatus.PENDING
        _carrierId = null
        _assignedBy = null
        _assignedAt = null
        return PenaltyRecord.create(penalizedCarrierId, id!!, PenaltyReason.REJECTED_FORCED_ASSIGNMENT, now)
    }

    /**
     * 배차를 취소한다. **멱등**: 이미 CANCELLED면 no-op(false, 기존 사유 유지), 실제 전이면 true.
     * 취소 불가 상태(TIMEOUT 등)면 충돌로 예외.
     */
    fun cancel(reason: String): Boolean {
        if (_status == DispatchStatus.CANCELLED) return false
        if (!_status.canTransitionTo(DispatchStatus.CANCELLED)) {
            throw DispatchNotCancellableException(_status)
        }
        _status = DispatchStatus.CANCELLED
        _cancelReason = reason
        return true
    }

    /**
     * PENDING 배차를 시한 초과로 종결한다. **멱등**: 이미 TIMEOUT이면 no-op(false), 실제 전이면 true.
     * 그 외 비-PENDING(ACCEPTED 등)이면 충돌로 예외(스위퍼는 건너뛴다).
     */
    fun timeout(): Boolean {
        if (_status == DispatchStatus.TIMEOUT) return false
        if (_status != DispatchStatus.PENDING) throw DispatchTimeoutNotAllowedException(_status)
        _status = DispatchStatus.TIMEOUT
        return true
    }

    /**
     * 만료 판정의 **유일한 근거**. 리드타임은 호출자(스위퍼)가 설정값으로 주입한다 —
     * 정책값을 도메인에 박아 두면 조회 SQL 과 두 곳에 존재하게 되고 한쪽만 바뀌면 어긋난다.
     *
     * 경계는 포함이다(`desiredPickupAt - lead == now` 면 만료). 조회 쿼리가 같은 경계로
     * 후보를 추리므로 둘이 일치해야 프리필터-판정 사이에 누락이 없다.
     */
    fun isExpired(now: Instant, lead: Duration): Boolean =
        _status == DispatchStatus.PENDING &&
            !now.isBefore(desiredPickupAt.minus(lead))
}
