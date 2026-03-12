package com.carry.serviceavailability.application.service

import com.carry.serviceavailability.application.port.inbound.ServiceAvailabilityQueryUseCase
import com.carry.serviceavailability.application.port.outbound.ServiceAreaPersistencePort
import com.carry.serviceavailability.domain.exception.ServiceAreaNotFoundException
import com.carry.serviceavailability.domain.model.ServiceArea
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class ServiceAvailabilityQueryService(
    private val serviceAreaPersistencePort: ServiceAreaPersistencePort,
) : ServiceAvailabilityQueryUseCase {

    override fun checkAvailability(areaCode: String, pickupAt: Instant, deliveryAt: Instant) {
        val area = serviceAreaPersistencePort.findByAreaCode(areaCode)
            ?: throw ServiceAreaNotFoundException(areaCode)
        area.checkAvailability(pickupAt)
        area.checkAvailability(deliveryAt)
    }

    override fun isAvailable(areaCode: String, pickupAt: Instant, deliveryAt: Instant): Boolean {
        val area = serviceAreaPersistencePort.findByAreaCode(areaCode) ?: return false
        return area.isAvailable(pickupAt) && area.isAvailable(deliveryAt)
    }

    override fun getByAreaCode(areaCode: String): ServiceArea {
        return serviceAreaPersistencePort.findByAreaCode(areaCode)
            ?: throw ServiceAreaNotFoundException(areaCode)
    }

    override fun getAllActive(): List<ServiceArea> {
        return serviceAreaPersistencePort.findAllActive()
    }
}
