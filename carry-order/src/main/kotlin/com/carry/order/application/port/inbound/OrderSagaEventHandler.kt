package com.carry.order.application.port.inbound

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.LaundryStartedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.dispatch.DispatchTimeoutEvent

interface OrderSagaEventHandler {
    fun onDispatchAccepted(event: DispatchAcceptedEvent)
    fun onDispatchTimeout(event: DispatchTimeoutEvent)
    fun onDispatchCancelled(event: DispatchCancelledEvent)
    fun onPickupCompleted(event: PickupCompletedEvent)
    fun onLaundryStarted(event: LaundryStartedEvent)
    fun onDeliveryCompleted(event: DeliveryCompletedEvent)
}
