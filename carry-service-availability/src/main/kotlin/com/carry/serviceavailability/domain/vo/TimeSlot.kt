package com.carry.serviceavailability.domain.vo

import com.carry.common.exception.requireInput
import java.time.LocalTime

data class TimeSlot(
    val openTime: LocalTime,
    val closeTime: LocalTime,
) {
    init {
        requireInput(openTime < closeTime) { "운영 시작 시간은 종료 시간보다 앞서야 합니다" }
    }

    fun contains(time: LocalTime): Boolean = time in openTime..closeTime
}
