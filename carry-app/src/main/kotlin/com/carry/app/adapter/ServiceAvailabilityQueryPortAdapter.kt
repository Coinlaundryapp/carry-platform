package com.carry.app.adapter

import com.carry.order.application.port.outbound.ServiceAvailabilityQueryPort
import com.carry.serviceavailability.application.port.inbound.ServiceAvailabilityQueryUseCase
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Cross-module adapter: carry-order -> carry-service-availability
 */
@Component
class ServiceAvailabilityQueryPortAdapter(
    private val serviceAvailabilityQueryUseCase: ServiceAvailabilityQueryUseCase,
) : ServiceAvailabilityQueryPort {

    override fun checkAvailability(areaCode: String, pickupAt: Instant, deliveryAt: Instant) {
        serviceAvailabilityQueryUseCase.checkAvailability(areaCode, pickupAt, deliveryAt)
    }
}
