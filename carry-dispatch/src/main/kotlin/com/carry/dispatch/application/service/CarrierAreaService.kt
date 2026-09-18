package com.carry.dispatch.application.service

import com.carry.dispatch.application.port.inbound.CarrierAreaUseCase
import com.carry.dispatch.application.port.inbound.RegisterAreaCommand
import com.carry.dispatch.application.port.inbound.RemoveAreaCommand
import com.carry.dispatch.application.port.outbound.CarrierAreaPersistencePort
import com.carry.dispatch.domain.exception.CarrierAreaNotFoundException
import com.carry.dispatch.domain.model.CarrierArea
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class CarrierAreaService(
    private val carrierAreaPersistencePort: CarrierAreaPersistencePort,
    private val clock: Clock,
) : CarrierAreaUseCase {

    @Transactional
    override fun registerArea(command: RegisterAreaCommand): CarrierArea {
        val existing = carrierAreaPersistencePort.findByCarrierIdAndAreaCode(command.carrierId, command.areaCode)
        if (existing != null) {
            existing.activate()
            return carrierAreaPersistencePort.save(existing)
        }
        val carrierArea = CarrierArea.create(command.carrierId, command.areaCode, command.areaName, clock.instant())
        return carrierAreaPersistencePort.save(carrierArea)
    }

    @Transactional
    override fun removeArea(command: RemoveAreaCommand) {
        val carrierArea = carrierAreaPersistencePort.findByCarrierIdAndAreaCode(command.carrierId, command.areaCode)
            ?: throw CarrierAreaNotFoundException(command.carrierId, command.areaCode)
        carrierArea.deactivate()
        carrierAreaPersistencePort.save(carrierArea)
    }

    @Transactional(readOnly = true)
    override fun getCarriersByArea(areaCode: String): List<CarrierArea> {
        return carrierAreaPersistencePort.findActiveByAreaCode(areaCode)
    }

    @Transactional(readOnly = true)
    override fun getAreasByCarrier(carrierId: Long): List<CarrierArea> {
        return carrierAreaPersistencePort.findByCarrierId(carrierId)
    }
}
