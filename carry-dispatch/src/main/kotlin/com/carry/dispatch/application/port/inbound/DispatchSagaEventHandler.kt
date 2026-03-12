package com.carry.dispatch.application.port.inbound

import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent

interface DispatchSagaEventHandler {
    fun onOrderCreated(event: OrderCreatedEvent)
    fun onOrderCancelled(event: OrderCancelledEvent)
}
