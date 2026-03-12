package com.carry.serviceavailability.application.port.inbound

import com.carry.serviceavailability.domain.model.ServiceArea
import java.time.Instant

interface ServiceAvailabilityQueryUseCase {
    fun checkAvailability(areaCode: String, pickupAt: Instant, deliveryAt: Instant)
    fun isAvailable(areaCode: String, pickupAt: Instant, deliveryAt: Instant): Boolean
    fun getByAreaCode(areaCode: String): ServiceArea
    fun getAllActive(): List<ServiceArea>
}
