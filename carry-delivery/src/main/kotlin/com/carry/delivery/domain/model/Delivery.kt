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
        ): Delivery {
            val now = Instant.now()
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

    fun completePickup(weight: BigDecimal, photoIds: List<Long>) {
        require(weight > BigDecimal.ZERO) { throw DeliveryWeightRequiredException() }
        require(photoIds.isNotEmpty()) { throw DeliveryPhotoRequiredException() }
        transitTo(DeliveryStatus.PICKED_UP)
        _actualWeight = weight
        getStep(DeliveryStepType.PICKUP)?.complete(photoIds)
        getStep(DeliveryStepType.WEIGHING)?.complete(emptyList())
    }

    fun startWashing(photoIds: List<Long>) {
        require(photoIds.isNotEmpty()) { throw DeliveryPhotoRequiredException() }
        transitTo(DeliveryStatus.IN_LAUNDRY)
        getStep(DeliveryStepType.WASHING)?.complete(photoIds)
    }

    fun completeDrying(photoIds: List<Long>) {
        require(photoIds.isNotEmpty()) { throw DeliveryPhotoRequiredException() }
        transitTo(DeliveryStatus.LAUNDRY_COMPLETE)
        getStep(DeliveryStepType.DRYING)?.complete(photoIds)
    }

    fun completeDelivery(photoIds: List<Long>) {
        require(photoIds.isNotEmpty()) { throw DeliveryPhotoRequiredException() }
        transitTo(DeliveryStatus.DELIVERED)
        getStep(DeliveryStepType.DELIVERY)?.complete(photoIds)
    }

    fun cancel() {
        transitTo(DeliveryStatus.CANCELLED)
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
