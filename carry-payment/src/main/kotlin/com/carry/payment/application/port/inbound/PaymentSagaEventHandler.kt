package com.carry.payment.application.port.inbound

import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.payment.InvoiceIssuedEvent

interface PaymentSagaEventHandler {
    fun onPickupCompleted(event: PickupCompletedEvent)
    fun onOrderCancelled(event: OrderCancelledEvent)

    /** carry-payment 모듈 자신이 발행한 InvoiceIssuedEvent 를 소비해 자동과금 사가를 시작한다. */
    fun onInvoiceIssued(event: InvoiceIssuedEvent)
}
