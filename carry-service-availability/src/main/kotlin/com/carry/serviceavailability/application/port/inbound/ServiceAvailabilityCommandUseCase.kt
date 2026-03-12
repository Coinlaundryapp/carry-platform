package com.carry.serviceavailability.application.port.inbound

import com.carry.serviceavailability.domain.model.ServiceArea
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

interface ServiceAvailabilityCommandUseCase {
    fun createServiceArea(command: CreateServiceAreaCommand): ServiceArea
    fun activateArea(areaId: Long): ServiceArea
    fun deactivateArea(areaId: Long): ServiceArea
    fun suspendArea(areaId: Long): ServiceArea
    fun setSchedule(command: SetScheduleCommand): ServiceArea
    fun removeSchedule(areaId: Long, dayOfWeek: DayOfWeek): ServiceArea
    fun addHoliday(command: AddHolidayCommand): ServiceArea
    fun removeHoliday(areaId: Long, date: LocalDate): ServiceArea
}

data class CreateServiceAreaCommand(
    val areaCode: String,
    val name: String,
)

data class SetScheduleCommand(
    val areaId: Long,
    val dayOfWeek: DayOfWeek,
    val openTime: LocalTime,
    val closeTime: LocalTime,
)

data class AddHolidayCommand(
    val areaId: Long,
    val date: LocalDate,
    val reason: String,
)
