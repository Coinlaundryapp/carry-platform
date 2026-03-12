package com.carry.payment.application.port.outbound

data class PgPaymentRequest(
    val orderId: Long,
    val amount: Long,
    val orderName: String,
    val customerName: String,
    val paymentKey: String,
)

data class PgPaymentResult(
    val success: Boolean,
    val pgTransactionId: String? = null,
    val failReason: String? = null,
)

data class PgCancelResult(
    val success: Boolean,
    val refundAmount: Long? = null,
    val failReason: String? = null,
)

interface PaymentGatewayPort {
    fun requestPayment(request: PgPaymentRequest): PgPaymentResult
    fun cancelPayment(pgTransactionId: String): PgCancelResult
}
