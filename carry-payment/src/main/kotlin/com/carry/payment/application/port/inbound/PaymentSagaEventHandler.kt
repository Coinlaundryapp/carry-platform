package com.carry.payment.application.port.inbound

import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.order.OrderCancelledEvent

interface PaymentSagaEventHandler {
    fun onPickupCompleted(event: PickupCompletedEvent)
    fun onOrderCancelled(event: OrderCancelledEvent)
}
