package com.carry.serviceavailability.domain.model

import com.carry.serviceavailability.domain.exception.AreaNotActiveException
import com.carry.serviceavailability.domain.exception.HolidayException
import com.carry.serviceavailability.domain.exception.OutsideOperatingHoursException
import com.carry.serviceavailability.domain.vo.AreaStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ServiceArea private constructor(
    val id: Long?,
    val areaCode: String,
    val name: String,
    private var _status: AreaStatus,
    private val _schedules: MutableList<OperatingSchedule>,
    private val _holidays: MutableList<HolidayOverride>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    val status get() = _status
    val schedules: List<OperatingSchedule> get() = _schedules.toList()
    val holidays: List<HolidayOverride> get() = _holidays.toList()

    fun activate() {
        _status = AreaStatus.ACTIVE
    }

    fun deactivate() {
        _status = AreaStatus.INACTIVE
    }

    fun suspend() {
        _status = AreaStatus.SUSPENDED
    }

    fun setSchedule(dayOfWeek: DayOfWeek, openTime: LocalTime, closeTime: LocalTime) {
        _schedules.removeIf { it.dayOfWeek == dayOfWeek }
        _schedules.add(OperatingSchedule.create(dayOfWeek, openTime, closeTime))
    }

    fun removeSchedule(dayOfWeek: DayOfWeek) {
        _schedules.removeIf { it.dayOfWeek == dayOfWeek }
    }

    fun addHoliday(date: LocalDate, reason: String) {
        _holidays.removeIf { it.date == date }
        _holidays.add(HolidayOverride.create(date, reason))
    }

    fun removeHoliday(date: LocalDate) {
        _holidays.removeIf { it.date == date }
    }

    fun checkAvailability(requestedAt: Instant, zoneId: ZoneId = KOREA_ZONE) {
        if (_status != AreaStatus.ACTIVE) {
            throw AreaNotActiveException(areaCode, _status)
        }

        val zonedDateTime = requestedAt.atZone(zoneId)
        val date = zonedDateTime.toLocalDate()
        val time = zonedDateTime.toLocalTime()
        val dayOfWeek = zonedDateTime.dayOfWeek

        val holiday = _holidays.find { it.date == date }
        if (holiday != null) {
            throw HolidayException(areaCode, date, holiday.reason)
        }

        val schedule = _schedules.find { it.dayOfWeek == dayOfWeek }
            ?: throw OutsideOperatingHoursException(areaCode, dayOfWeek)

        if (!schedule.timeSlot.contains(time)) {
            throw OutsideOperatingHoursException(areaCode, dayOfWeek, time, schedule.openTime, schedule.closeTime)
        }
    }

    fun isAvailable(requestedAt: Instant, zoneId: ZoneId = KOREA_ZONE): Boolean {
        return try {
            checkAvailability(requestedAt, zoneId)
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private val KOREA_ZONE = ZoneId.of("Asia/Seoul")

        fun create(areaCode: String, name: String): ServiceArea {
            require(areaCode.isNotBlank()) { "지역 코드는 비어있을 수 없습니다" }
            require(name.isNotBlank()) { "지역명은 비어있을 수 없습니다" }
            return ServiceArea(
                id = null,
                areaCode = areaCode,
                name = name,
                _status = AreaStatus.INACTIVE,
                _schedules = mutableListOf(),
                _holidays = mutableListOf(),
                createdAt = null,
                updatedAt = null,
            )
        }

        fun reconstitute(
            id: Long,
            areaCode: String,
            name: String,
            status: AreaStatus,
            schedules: List<OperatingSchedule>,
            holidays: List<HolidayOverride>,
            createdAt: Instant,
            updatedAt: Instant,
        ): ServiceArea = ServiceArea(
            id = id,
            areaCode = areaCode,
            name = name,
            _status = status,
            _schedules = schedules.toMutableList(),
            _holidays = holidays.toMutableList(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
