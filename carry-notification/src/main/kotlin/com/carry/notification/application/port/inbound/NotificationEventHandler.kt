package com.carry.notification.application.port.inbound

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent

interface NotificationEventHandler {
    fun onOrderCreated(event: OrderCreatedEvent)
    fun onDispatchAccepted(event: DispatchAcceptedEvent)
    fun onPickupCompleted(event: PickupCompletedEvent)
    fun onInvoiceIssued(event: InvoiceIssuedEvent)
    fun onPaymentCompleted(event: PaymentCompletedEvent)
    fun onDeliveryCompleted(event: DeliveryCompletedEvent)
}
