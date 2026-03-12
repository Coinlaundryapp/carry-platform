package com.carry.operation.application.port.inbound

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.payment.PaymentCompletedEvent

interface OperationEventHandler {
    fun onOrderCreated(event: OrderCreatedEvent)
    fun onOrderCancelled(event: OrderCancelledEvent)
    fun onDispatchAccepted(event: DispatchAcceptedEvent)
    fun onDeliveryCompleted(event: DeliveryCompletedEvent)
    fun onPaymentCompleted(event: PaymentCompletedEvent)
}
