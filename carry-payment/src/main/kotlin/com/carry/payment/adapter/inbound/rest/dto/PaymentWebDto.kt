package com.carry.payment.adapter.inbound.rest.dto

import com.carry.payment.domain.model.Invoice
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.InvoiceLineItem
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant

data class PaymentRequest(
    @field:NotBlank val pgProvider: String,
    @field:NotBlank val paymentKey: String,
)

data class InvoiceResponse(
    val id: Long,
    val orderId: Long,
    val customerId: Long,
    val status: String,
    val lineItems: List<InvoiceLineItemResponse>,
    val weight: BigDecimal,
    val totalAmount: Long,
    val createdAt: Instant,
) {
    companion object {
        fun from(invoice: Invoice) = InvoiceResponse(
            id = invoice.id!!,
            orderId = invoice.orderId,
            customerId = invoice.customerId,
            status = invoice.status.name,
            lineItems = invoice.lineItems.map { InvoiceLineItemResponse.from(it) },
            weight = invoice.weight,
            totalAmount = invoice.totalAmount,
            createdAt = invoice.createdAt,
        )
    }
}

data class InvoiceLineItemResponse(
    val chargeType: String,
    val description: String,
    val amount: Long,
) {
    companion object {
        fun from(item: InvoiceLineItem) = InvoiceLineItemResponse(
            chargeType = item.chargeType.name,
            description = item.description,
            amount = item.amount,
        )
    }
}

data class PaymentResponse(
    val id: Long,
    val invoiceId: Long,
    val orderId: Long,
    val customerId: Long,
    val status: String,
    val pgProvider: String,
    val pgTransactionId: String?,
    val amount: Long,
    val paidAt: Instant?,
    val failReason: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(payment: Payment) = PaymentResponse(
            id = payment.id!!,
            invoiceId = payment.invoiceId,
            orderId = payment.orderId,
            customerId = payment.customerId,
            status = payment.status.name,
            pgProvider = payment.pgProvider.name,
            pgTransactionId = payment.pgTransactionId,
            amount = payment.amount,
            paidAt = payment.paidAt,
            failReason = payment.failReason,
            createdAt = payment.createdAt,
        )
    }
}
