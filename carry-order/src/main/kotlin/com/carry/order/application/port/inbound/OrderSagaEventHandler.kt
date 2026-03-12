package com.carry.order.application.port.inbound

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.LaundryStartedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.dispatch.DispatchTimeoutEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.event.payment.PaymentFailedEvent
import com.carry.event.payment.RefundCompletedEvent

interface OrderSagaEventHandler {
    fun onDispatchAccepted(event: DispatchAcceptedEvent)
    fun onDispatchTimeout(event: DispatchTimeoutEvent)
    fun onDispatchCancelled(event: DispatchCancelledEvent)
    fun onPickupCompleted(event: PickupCompletedEvent)
    fun onInvoiceIssued(event: InvoiceIssuedEvent)
    fun onPaymentCompleted(event: PaymentCompletedEvent)
    fun onPaymentFailed(event: PaymentFailedEvent)
    fun onLaundryStarted(event: LaundryStartedEvent)
    fun onDeliveryCompleted(event: DeliveryCompletedEvent)
    fun onRefundCompleted(event: RefundCompletedEvent)
}
