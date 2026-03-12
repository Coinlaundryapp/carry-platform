package com.carry.serviceavailability.domain.model

import com.carry.serviceavailability.domain.vo.TimeSlot
import java.time.DayOfWeek
import java.time.LocalTime

class OperatingSchedule private constructor(
    val id: Long?,
    val dayOfWeek: DayOfWeek,
    private var _openTime: LocalTime,
    private var _closeTime: LocalTime,
) {
    val openTime get() = _openTime
    val closeTime get() = _closeTime
    val timeSlot get() = TimeSlot(_openTime, _closeTime)

    fun update(openTime: LocalTime, closeTime: LocalTime) {
        require(openTime < closeTime) { "운영 시작 시간은 종료 시간보다 앞서야 합니다" }
        _openTime = openTime
        _closeTime = closeTime
    }

    companion object {
        fun create(dayOfWeek: DayOfWeek, openTime: LocalTime, closeTime: LocalTime): OperatingSchedule {
            require(openTime < closeTime) { "운영 시작 시간은 종료 시간보다 앞서야 합니다" }
            return OperatingSchedule(null, dayOfWeek, openTime, closeTime)
        }

        fun reconstitute(
            id: Long,
            dayOfWeek: DayOfWeek,
            openTime: LocalTime,
            closeTime: LocalTime,
        ): OperatingSchedule = OperatingSchedule(id, dayOfWeek, openTime, closeTime)
    }
}
