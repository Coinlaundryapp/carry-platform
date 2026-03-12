package com.carry.serviceavailability.application.service

import com.carry.serviceavailability.application.port.inbound.AddHolidayCommand
import com.carry.serviceavailability.application.port.inbound.CreateServiceAreaCommand
import com.carry.serviceavailability.application.port.inbound.ServiceAvailabilityCommandUseCase
import com.carry.serviceavailability.application.port.inbound.SetScheduleCommand
import com.carry.serviceavailability.domain.exception.DuplicateServiceAreaException
import com.carry.serviceavailability.domain.exception.ServiceAreaNotFoundByIdException
import com.carry.serviceavailability.domain.model.ServiceArea
import com.carry.serviceavailability.application.port.outbound.ServiceAreaPersistencePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate

@Service
@Transactional
class ServiceAvailabilityCommandService(
    private val serviceAreaPersistencePort: ServiceAreaPersistencePort,
) : ServiceAvailabilityCommandUseCase {

    override fun createServiceArea(command: CreateServiceAreaCommand): ServiceArea {
        if (serviceAreaPersistencePort.existsByAreaCode(command.areaCode)) {
            throw DuplicateServiceAreaException(command.areaCode)
        }
        val area = ServiceArea.create(command.areaCode, command.name)
        return serviceAreaPersistencePort.save(area)
    }

    override fun activateArea(areaId: Long): ServiceArea {
        val area = findById(areaId)
        area.activate()
        return serviceAreaPersistencePort.save(area)
    }

    override fun deactivateArea(areaId: Long): ServiceArea {
        val area = findById(areaId)
        area.deactivate()
        return serviceAreaPersistencePort.save(area)
    }

    override fun suspendArea(areaId: Long): ServiceArea {
        val area = findById(areaId)
        area.suspend()
        return serviceAreaPersistencePort.save(area)
    }

    override fun setSchedule(command: SetScheduleCommand): ServiceArea {
        val area = findById(command.areaId)
        area.setSchedule(command.dayOfWeek, command.openTime, command.closeTime)
        return serviceAreaPersistencePort.save(area)
    }

    override fun removeSchedule(areaId: Long, dayOfWeek: DayOfWeek): ServiceArea {
        val area = findById(areaId)
        area.removeSchedule(dayOfWeek)
        return serviceAreaPersistencePort.save(area)
    }

    override fun addHoliday(command: AddHolidayCommand): ServiceArea {
        val area = findById(command.areaId)
        area.addHoliday(command.date, command.reason)
        return serviceAreaPersistencePort.save(area)
    }

    override fun removeHoliday(areaId: Long, date: LocalDate): ServiceArea {
        val area = findById(areaId)
        area.removeHoliday(date)
        return serviceAreaPersistencePort.save(area)
    }

    private fun findById(id: Long): ServiceArea {
        return serviceAreaPersistencePort.findById(id)
            ?: throw ServiceAreaNotFoundByIdException(id)
    }
}
