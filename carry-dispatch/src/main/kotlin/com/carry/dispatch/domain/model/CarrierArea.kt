package com.carry.dispatch.domain.model

import com.carry.common.exception.requireInput
import java.time.Instant

class CarrierArea private constructor(
    val id: Long?,
    val carrierId: Long,
    val areaCode: String,
    val areaName: String,
    private var _active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val active get() = _active

    companion object {
        fun create(carrierId: Long, areaCode: String, areaName: String, now: Instant): CarrierArea {
            requireInput(areaCode.isNotBlank()) { "구역 코드는 필수입니다" }
            return CarrierArea(null, carrierId, areaCode, areaName, true, now, now)
        }

        fun reconstitute(
            id: Long, carrierId: Long, areaCode: String, areaName: String,
            active: Boolean, createdAt: Instant, updatedAt: Instant,
        ): CarrierArea = CarrierArea(id, carrierId, areaCode, areaName, active, createdAt, updatedAt)
    }

    fun deactivate() { _active = false }
    fun activate() { _active = true }
}
