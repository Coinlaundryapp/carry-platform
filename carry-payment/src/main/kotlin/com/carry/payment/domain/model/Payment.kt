package com.carry.payment.domain.model

import com.carry.common.exception.checkState
import com.carry.common.exception.requireInput
import com.carry.payment.domain.exception.PaymentAlreadyCompletedException
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import java.time.Duration
import java.time.Instant

class Payment private constructor(
    val id: Long?,
    val invoiceId: Long,
    val orderId: Long,
    val customerId: Long,
    private var _status: PaymentStatus,
    val pgProvider: PgProvider,
    private var _pgTransactionId: String?,
    val amount: Long,
    private var _paidAt: Instant?,
    private var _failReason: String?,
    private var _retryCount: Int,
    private var _nextRetryAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val status get() = _status
    val pgTransactionId get() = _pgTransactionId
    val paidAt get() = _paidAt
    val failReason get() = _failReason
    val retryCount get() = _retryCount
    val nextRetryAt get() = _nextRetryAt

    companion object {
        // 의도적 스펙 이탈 기록: 백오프 스케줄은 도메인 규칙(순수 함수)이라 도메인에 상수로 두고,
        // 스윕 주기·연체 임계만 설정으로 뺀다.
        private val BACKOFF = listOf(
            Duration.ofHours(1), Duration.ofHours(4), Duration.ofHours(12), Duration.ofHours(24),
        )
        fun backoffFor(retryCount: Int): Duration = BACKOFF.getOrElse(retryCount - 1) { Duration.ofHours(24) }

        fun create(
            invoiceId: Long,
            orderId: Long,
            customerId: Long,
            pgProvider: PgProvider,
            amount: Long,
            now: Instant,
        ): Payment {
            requireInput(amount > 0) { "결제 금액은 0보다 커야 합니다: $amount" }

            return Payment(
                id = null,
                invoiceId = invoiceId,
                orderId = orderId,
                customerId = customerId,
                _status = PaymentStatus.PENDING,
                pgProvider = pgProvider,
                _pgTransactionId = null,
                amount = amount,
                _paidAt = null,
                _failReason = null,
                _retryCount = 0,
                _nextRetryAt = null,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            invoiceId: Long,
            orderId: Long,
            customerId: Long,
            status: PaymentStatus,
            pgProvider: PgProvider,
            pgTransactionId: String?,
            amount: Long,
            paidAt: Instant?,
            failReason: String?,
            retryCount: Int = 0,
            nextRetryAt: Instant? = null,
            createdAt: Instant,
            updatedAt: Instant,
        ): Payment = Payment(
            id, invoiceId, orderId, customerId, status, pgProvider,
            pgTransactionId, amount, paidAt, failReason, retryCount, nextRetryAt, createdAt, updatedAt,
        )
    }

    fun markCompleted(pgTransactionId: String, now: Instant) {
        // 중복 완료만 전용 코드로 구분한다(409 동일) — docs/14 가 PAYMENT_ALREADY_COMPLETED 를
        // "이미 그렇게 됐으니 재시도 말고 상태를 조회하라" 로 안내한다. 그 외 전이는 일반 충돌.
        if (_status == PaymentStatus.COMPLETED) throw PaymentAlreadyCompletedException(id)
        transitTo(PaymentStatus.COMPLETED)
        _pgTransactionId = pgTransactionId
        _paidAt = now
    }

    fun markFailed(reason: String) {
        transitTo(PaymentStatus.FAILED)
        _failReason = reason
    }

    /** 환불 의도 표시(COMPLETED→REFUND_PENDING). 실제 PG 취소는 [markRefunded] 직전에 이뤄진다. */
    fun markRefundPending() {
        transitTo(PaymentStatus.REFUND_PENDING)
    }

    /** PG 취소 성공 후 호출(REFUND_PENDING→REFUNDED). */
    fun markRefunded() {
        transitTo(PaymentStatus.REFUNDED)
    }

    /** 과금 실패 후 다음 재시도 예약. 백오프: 1h → 4h → 12h → 24h → 이후 24h 고정. */
    fun scheduleRetry(now: Instant) {
        checkState(_status == PaymentStatus.FAILED) { "FAILED 상태에서만 재시도를 예약할 수 있습니다" }
        _retryCount += 1
        _nextRetryAt = now.plus(backoffFor(_retryCount))
    }

    /** 스위퍼가 재과금 직전 호출 — FAILED → PENDING 재전이(기존 전이 규칙에 존재). */
    fun markRetrying() {
        transitTo(PaymentStatus.PENDING)
        _nextRetryAt = null
    }

    private fun transitTo(target: PaymentStatus) {
        checkState(_status.canTransitionTo(target)) {
            "결제 상태 전이가 유효하지 않습니다: $_status → $target"
        }
        _status = target
    }
}
