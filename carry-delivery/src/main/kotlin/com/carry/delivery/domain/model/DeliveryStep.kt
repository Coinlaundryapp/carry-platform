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

    /**
     * 스텝을 완료로 고정한다. **이미 완료된 스텝은 건드리지 않고 false 를 돌려준다.**
     *
     * 지금은 [Delivery] 의 상태 가드가 중복 호출을 막고 있지만, 그것이 유일한 보호막이면 호출 경로가
     * 하나 늘어날 때 조용히 깨진다 — 재호출 시 증빙 사진이 중복 누적되고 `completedAt`·`note` 가
     * 덮어써져 "언제 무엇으로 완료했는가" 가 사라진다. 애그리거트 상태전이와 같은 자연 멱등
     * (no-op 이면 false)을 스텝에도 둔다.
     */
    fun complete(mediaIds: List<Long>, now: Instant, note: String? = null): Boolean {
        if (_status == StepStatus.COMPLETED) return false
        _status = StepStatus.COMPLETED
        _mediaIds.addAll(mediaIds)
        _note = note
        _completedAt = now
        return true
    }
}
