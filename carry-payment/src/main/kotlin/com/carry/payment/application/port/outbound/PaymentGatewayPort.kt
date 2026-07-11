package com.carry.payment.application.port.outbound

import java.time.Instant

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

enum class PgTransactionType {
    CHARGE, CANCEL,
}

/** PG 측 원장 기록 한 건 — 대사(reconciliation) 잡이 로컬 결제와 diff 하는 단위. */
data class PgTransactionRecord(
    val pgTransactionId: String,
    val type: PgTransactionType,
    val amount: Long,
    val occurredAt: Instant,
)

interface PaymentGatewayPort {
    fun requestPayment(request: PgPaymentRequest): PgPaymentResult
    fun cancelPayment(pgTransactionId: String): PgCancelResult

    /** 기간 내 PG 측 거래(과금·취소) 목록 — 대사 잡 전용. 실 PG 도입 시 동일 시그니처로 연결. */
    fun listTransactions(from: Instant, to: Instant): List<PgTransactionRecord>
}
