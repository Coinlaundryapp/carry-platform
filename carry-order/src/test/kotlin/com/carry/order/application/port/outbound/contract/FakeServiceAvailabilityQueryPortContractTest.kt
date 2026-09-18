package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.ServiceAvailabilityQueryPort

class FakeServiceAvailabilityQueryPortContractTest : ServiceAvailabilityQueryPortContract() {

    private val fake = FakeServiceAvailabilityQueryPort()

    override fun subject(): ServiceAvailabilityQueryPort = fake

    override fun arrangeAvailable() {
        fake.markAvailable(areaCode)
    }

    override fun arrangeMissingArea() {
        // markAvailable 하지 않음
    }

    override fun arrangeDeliveryOutsideHours() {
        fake.markUnavailableAt(areaCode, deliveryAt)
    }

    override fun arrangePickupOutsideHours() {
        fake.markUnavailableAt(areaCode, pickupAt)
    }
}
