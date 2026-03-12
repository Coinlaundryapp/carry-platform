package com.carry.event.payment

data class InvoiceIssuedEvent(
    val invoiceId: Long,
    val orderId: Long,
    val totalAmount: Long,
    val lineItems: List<InvoiceLineItemDto>,
)

data class InvoiceLineItemDto(
    val chargeType: String,
    val description: String,
    val amount: Long,
)

data class PaymentCompletedEvent(
    val paymentId: Long,
    val orderId: Long,
    val invoiceId: Long,
    val amount: Long,
)

data class PaymentFailedEvent(
    val paymentId: Long,
    val orderId: Long,
    val reason: String,
)

data class RefundCompletedEvent(
    val paymentId: Long,
    val orderId: Long,
    val refundAmount: Long,
)
