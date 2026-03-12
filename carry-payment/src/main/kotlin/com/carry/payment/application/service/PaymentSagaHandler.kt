package com.carry.payment.application.service

import com.carry.event.delivery.PickupCompletedEvent
import com.carry.payment.application.port.inbound.PaymentSagaEventHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentSagaHandler(
    private val invoiceService: InvoiceService,
) : PaymentSagaEventHandler {

    @Transactional
    override fun onPickupCompleted(event: PickupCompletedEvent) {
        invoiceService.createInvoiceFromPickup(event)
    }
}
