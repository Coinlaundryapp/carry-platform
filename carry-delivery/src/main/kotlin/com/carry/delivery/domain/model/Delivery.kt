package com.carry.delivery.domain.model

import com.carry.delivery.domain.exception.DeliveryNotInExpectedStatusException
import com.carry.delivery.domain.exception.DeliveryPhotoRequiredException
import com.carry.delivery.domain.exception.DeliveryWeightRequiredException
import com.carry.delivery.domain.vo.DeliveryStatus
import com.carry.delivery.domain.vo.DeliveryStepType
import java.math.BigDecimal
import java.time.Instant

class Delivery private constructor(
    val id: Long?,
    val orderId: Long,
    val dispatchId: Long,
    val carrierId: Long,
    val laundromatId: Long,
    private var _status: DeliveryStatus,
    private var _actualWeight: BigDecimal?,
    private val _steps: MutableList<DeliveryStep>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val status get() = _status
    val actualWeight get() = _actualWeight
    val steps: List<DeliveryStep> get() = _steps.toList()

    companion object {
        fun create(
            orderId: Long,
            dispatchId: Long,
            carrierId: Long,
            laundromatId: Long,
            now: Instant,
        ): Delivery {
            val steps = mutableListOf(
                DeliveryStep.createPending(DeliveryStepType.PICKUP),
                DeliveryStep.createPending(DeliveryStepType.WEIGHING),
                DeliveryStep.createPending(DeliveryStepType.WASHING),
                DeliveryStep.createPending(DeliveryStepType.DRYING),
                DeliveryStep.createPending(DeliveryStepType.DELIVERY),
            )
            return Delivery(
                id = null,
                orderId = orderId,
                dispatchId = dispatchId,
                carrierId = carrierId,
                laundromatId = laundromatId,
                _status = DeliveryStatus.PICKUP_PENDING,
                _actualWeight = null,
                _steps = steps,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            orderId: Long,
            dispatchId: Long,
            carrierId: Long,
            laundromatId: Long,
            status: DeliveryStatus,
            actualWeight: BigDecimal?,
            steps: List<DeliveryStep>,
            createdAt: Instant,
            updatedAt: Instant,
        ): Delivery = Delivery(
            id = id,
            orderId = orderId,
            dispatchId = dispatchId,
            carrierId = carrierId,
            laundromatId = laundromatId,
            _status = status,
            _actualWeight = actualWeight,
            _steps = steps.toMutableList(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    // 각 상태전이는 **멱등**이다: 이미 목표 상태면 no-op(false), 실제 전이면 true, 그 외 비정상 전이면
    // transitTo가 예외(충돌). 선형 상태기계라 행위자는 항상 배정 캐리어 본인(소유 검증은 서비스).
    // 재시도가 이벤트를 재발행하지 않도록(사가 이중 트리거 방지) 서비스가 transitioned로 발행을 가린다.

    fun completePickup(weight: BigDecimal, photoIds: List<Long>, now: Instant): Boolean {
        if (_status == DeliveryStatus.PICKED_UP) return false
        if (weight <= BigDecimal.ZERO) throw DeliveryWeightRequiredException()
        if (photoIds.isEmpty()) throw DeliveryPhotoRequiredException()
        transitTo(DeliveryStatus.PICKED_UP)
        _actualWeight = weight
        getStep(DeliveryStepType.PICKUP)?.complete(photoIds, now)
        getStep(DeliveryStepType.WEIGHING)?.complete(emptyList(), now)
        return true
    }

    fun startWashing(photoIds: List<Long>, now: Instant): Boolean {
        if (_status == DeliveryStatus.IN_LAUNDRY) return false
        if (photoIds.isEmpty()) throw DeliveryPhotoRequiredException()
        transitTo(DeliveryStatus.IN_LAUNDRY)
        getStep(DeliveryStepType.WASHING)?.complete(photoIds, now)
        return true
    }

    fun completeDrying(photoIds: List<Long>, now: Instant): Boolean {
        if (_status == DeliveryStatus.LAUNDRY_COMPLETE) return false
        if (photoIds.isEmpty()) throw DeliveryPhotoRequiredException()
        transitTo(DeliveryStatus.LAUNDRY_COMPLETE)
        getStep(DeliveryStepType.DRYING)?.complete(photoIds, now)
        return true
    }

    fun startDelivery(): Boolean {
        if (_status == DeliveryStatus.DELIVERY_PENDING) return false
        transitTo(DeliveryStatus.DELIVERY_PENDING)
        return true
    }

    fun completeDelivery(photoIds: List<Long>, now: Instant): Boolean {
        if (_status == DeliveryStatus.DELIVERED) return false
        if (photoIds.isEmpty()) throw DeliveryPhotoRequiredException()
        transitTo(DeliveryStatus.DELIVERED)
        getStep(DeliveryStepType.DELIVERY)?.complete(photoIds, now)
        return true
    }

    fun cancel(): Boolean {
        if (_status == DeliveryStatus.CANCELLED) return false
        transitTo(DeliveryStatus.CANCELLED)
        return true
    }

    fun getStep(stepType: DeliveryStepType): DeliveryStep? {
        return _steps.find { it.stepType == stepType }
    }

    private fun transitTo(target: DeliveryStatus) {
        if (!_status.canTransitionTo(target)) {
            throw DeliveryNotInExpectedStatusException(id, _status, target)
        }
        _status = target
    }
}
