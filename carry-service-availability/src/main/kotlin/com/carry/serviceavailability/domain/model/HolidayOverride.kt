package com.carry.serviceavailability.domain.model

import java.time.LocalDate

class HolidayOverride private constructor(
    val id: Long?,
    val date: LocalDate,
    val reason: String,
) {
    companion object {
        fun create(date: LocalDate, reason: String): HolidayOverride {
            require(reason.isNotBlank()) { "휴무 사유는 비어있을 수 없습니다" }
            return HolidayOverride(null, date, reason)
        }

        fun reconstitute(id: Long, date: LocalDate, reason: String): HolidayOverride =
            HolidayOverride(id, date, reason)
    }
}
