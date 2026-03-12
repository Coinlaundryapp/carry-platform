package com.carry.order.application.port.outbound

import java.time.Instant

interface ServiceAvailabilityQueryPort {
    fun checkAvailability(areaCode: String, pickupAt: Instant, deliveryAt: Instant)
}
