package com.carry.delivery.application.port.inbound

import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.order.OrderCancelledEvent

interface DeliverySagaEventHandler {
    fun onDispatchAccepted(event: DispatchAcceptedEvent)
    fun onOrderCancelled(event: OrderCancelledEvent)
    fun onDispatchCancelled(event: DispatchCancelledEvent)
}
