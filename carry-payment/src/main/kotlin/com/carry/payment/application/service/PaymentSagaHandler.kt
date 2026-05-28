package com.carry.payment.application.service

import com.carry.common.logging.SagaLogContext
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.payment.application.port.inbound.PaymentSagaEventHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentSagaHandler(
    private val invoiceService: InvoiceService,
) : PaymentSagaEventHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun onPickupCompleted(event: PickupCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Payment saga: onPickupCompleted deliveryId={}", event.deliveryId)
            invoiceService.createInvoiceFromPickup(event)
        }
    }
}
