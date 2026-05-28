package com.carry.payment.application.service

import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.InvoiceLineItemDto
import com.carry.event.port.EventPublisherPort
import com.carry.payment.application.port.inbound.InvoiceQueryUseCase
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.domain.exception.InvoiceNotFoundException
import com.carry.payment.domain.model.Invoice
import com.carry.payment.domain.vo.ChargeType
import com.carry.payment.domain.vo.InvoiceLineItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class InvoiceService(
    private val invoicePersistencePort: InvoicePersistencePort,
    private val eventPublisher: EventPublisherPort,
) : InvoiceQueryUseCase {

    companion object {
        private const val BASE_RATE_PER_KG = 3000L
        private const val DELIVERY_FEE = 3000L
        private const val SERVICE_FEE_RATE = 0.1
    }

    @Transactional
    fun createInvoiceFromPickup(event: PickupCompletedEvent): Invoice {
        val laundryPrice = event.actualWeight.multiply(BigDecimal(BASE_RATE_PER_KG)).toLong()
        val serviceFee = (laundryPrice * SERVICE_FEE_RATE).toLong()

        val lineItems = listOf(
            InvoiceLineItem(ChargeType.LAUNDRY_PRICE, "세탁 비용 (${event.actualWeight}kg)", laundryPrice),
            InvoiceLineItem(ChargeType.DELIVERY_FEE, "배달비", DELIVERY_FEE),
            InvoiceLineItem(ChargeType.SERVICE_FEE, "서비스 수수료", serviceFee),
        )

        val invoice = Invoice.create(
            orderId = event.orderId,
            customerId = event.customerId,
            lineItems = lineItems,
            weight = event.actualWeight,
        )

        val saved = invoicePersistencePort.save(invoice)

        eventPublisher.publish(
            aggregateType = "Payment",
            aggregateId = event.orderId.toString(),
            eventType = "InvoiceIssuedEvent",
            payload = InvoiceIssuedEvent(
                invoiceId = saved.id!!,
                orderId = saved.orderId,
                totalAmount = saved.totalAmount,
                lineItems = saved.lineItems.map {
                    InvoiceLineItemDto(it.chargeType.name, it.description, it.amount)
                },
            ),
        )

        return saved
    }

    @Transactional(readOnly = true)
    override fun getInvoice(invoiceId: Long): Invoice {
        return invoicePersistencePort.findById(invoiceId)
            ?: throw InvoiceNotFoundException(invoiceId.toString())
    }

    @Transactional(readOnly = true)
    override fun getInvoiceByOrder(orderId: Long): Invoice {
        return invoicePersistencePort.findByOrderId(orderId)
            ?: throw InvoiceNotFoundException("orderId=$orderId")
    }
}
