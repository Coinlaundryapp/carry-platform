package com.carry.delivery.domain.model

import com.carry.delivery.domain.vo.DeliveryStepType
import com.carry.delivery.domain.vo.StepStatus
import java.time.Instant

class DeliveryStep private constructor(
    val id: Long?,
    val deliveryId: Long?,
    val stepType: DeliveryStepType,
    private var _status: StepStatus,
    private val _mediaIds: MutableList<Long>,
    private var _note: String?,
    private var _completedAt: Instant?,
) {
    val status get() = _status
    val mediaIds: List<Long> get() = _mediaIds.toList()
    val note get() = _note
    val completedAt get() = _completedAt

    companion object {
        fun createPending(stepType: DeliveryStepType): DeliveryStep = DeliveryStep(
            id = null,
            deliveryId = null,
            stepType = stepType,
            _status = StepStatus.PENDING,
            _mediaIds = mutableListOf(),
            _note = null,
            _completedAt = null,
        )

        fun reconstitute(
            id: Long,
            deliveryId: Long,
            stepType: DeliveryStepType,
            status: StepStatus,
            mediaIds: List<Long>,
            note: String?,
            completedAt: Instant?,
        ): DeliveryStep = DeliveryStep(
            id = id,
            deliveryId = deliveryId,
            stepType = stepType,
            _status = status,
            _mediaIds = mediaIds.toMutableList(),
            _note = note,
            _completedAt = completedAt,
        )
    }

    fun complete(mediaIds: List<Long>, note: String? = null) {
        _status = StepStatus.COMPLETED
        _mediaIds.addAll(mediaIds)
        _note = note
        _completedAt = Instant.now()
    }
}
