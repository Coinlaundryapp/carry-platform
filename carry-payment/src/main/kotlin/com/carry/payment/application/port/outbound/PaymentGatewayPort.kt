package com.carry.payment.application.port.outbound

import java.time.Instant

data class PgBillingKeyRequest(
    val authKey: String,
    val customerKey: String,
)

data class PgBillingKeyResult(
    val success: Boolean,
    val billingKey: String? = null,
    val cardCompany: String? = null,
    val cardLast4: String? = null,
    val failReason: String? = null,
)

data class PgBillingChargeRequest(
    val billingKey: String,
    val customerKey: String,
    val orderId: Long,
    val amount: Long,
    val orderName: String,
    /**
     * PG 측 dedup 멱등키 — `charge-{invoiceId}` 로 고정, 재시도에도 동일 값 재전달.
     * 멱등성의 키 단위는 invoice 이며, [orderId] 는 PG 거래 기록의 표시·참조용 데이터일 뿐 dedup 에 관여하지 않는다.
     */
    val idempotencyKey: String,
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
    /** 빌링키 발급 — 결제 발생 없음. authKey는 프론트 SDK 카드 등록창 결과(mock에선 임의 문자열). */
    fun issueBillingKey(request: PgBillingKeyRequest): PgBillingKeyResult

    /** 빌링키 자동과금 — 사용자 액션 없이 서버 단독 호출. */
    fun chargeBilling(request: PgBillingChargeRequest): PgPaymentResult

    /**
     * PG 결제 취소(환불). [idempotencyKey] 는 재시도 시 PG 가 dedup 하는 결정적 키
     * (전액 환불은 `refund-{paymentId}`) — "PG 성공·로컬 마킹 실패" 후 스위퍼 재호출이
     * 이중환불이 되지 않도록 실 어댑터는 반드시 `Idempotency-Key` 헤더로 전달한다.
     */
    fun cancelPayment(pgTransactionId: String, idempotencyKey: String): PgCancelResult

    /** 기간 내 PG 측 거래(과금·취소) 목록 — 대사 잡 전용. 실 PG 도입 시 동일 시그니처로 연결. */
    fun listTransactions(from: Instant, to: Instant): List<PgTransactionRecord>
}
