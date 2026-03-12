package com.carry.payment.application.port.inbound

import com.carry.event.delivery.PickupCompletedEvent

interface PaymentSagaEventHandler {
    fun onPickupCompleted(event: PickupCompletedEvent)
}
