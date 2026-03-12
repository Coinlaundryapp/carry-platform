package com.carry.event.payment

data class PaymentCompletedEvent(
    val paymentId: Long,
    val orderId: Long,
    val amount: Long
)

data class PaymentFailedEvent(
    val paymentId: Long,
    val orderId: Long,
    val reason: String
)

data class RefundCompletedEvent(
    val paymentId: Long,
    val orderId: Long,
    val refundAmount: Long
)
