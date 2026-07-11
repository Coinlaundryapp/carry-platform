package com.carry.payment.application.port.outbound

import java.time.Instant

data class PgPaymentRequest(
    val orderId: Long,
    val amount: Long,
    val orderName: String,
    val customerName: String,
    val paymentKey: String,
    /**
     * PG 측 dedup 용 멱등키(실 어댑터는 `Idempotency-Key` 헤더로 전달).
     * 결제 요청은 클라이언트 paymentKey 가 곧 요청측 멱등키 — 재시도 시 동일 값이 재전달된다.
     */
    val idempotencyKey: String = paymentKey,
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

    /**
     * PG 결제 취소(환불). [idempotencyKey] 는 재시도 시 PG 가 dedup 하는 결정적 키
     * (전액 환불은 `refund-{paymentId}`) — "PG 성공·로컬 마킹 실패" 후 스위퍼 재호출이
     * 이중환불이 되지 않도록 실 어댑터는 반드시 `Idempotency-Key` 헤더로 전달한다.
     */
    fun cancelPayment(pgTransactionId: String, idempotencyKey: String): PgCancelResult

    /** 기간 내 PG 측 거래(과금·취소) 목록 — 대사 잡 전용. 실 PG 도입 시 동일 시그니처로 연결. */
    fun listTransactions(from: Instant, to: Instant): List<PgTransactionRecord>
}
