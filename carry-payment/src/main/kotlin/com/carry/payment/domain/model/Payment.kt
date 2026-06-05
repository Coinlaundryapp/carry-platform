package com.carry.payment.domain.model

import com.carry.common.exception.checkState
import com.carry.common.exception.requireInput
import com.carry.payment.domain.exception.PaymentAlreadyCompletedException
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
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
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val status get() = _status
    val pgTransactionId get() = _pgTransactionId
    val paidAt get() = _paidAt
    val failReason get() = _failReason

    companion object {
        fun create(
            invoiceId: Long,
            orderId: Long,
            customerId: Long,
            pgProvider: PgProvider,
            amount: Long,
        ): Payment {
            requireInput(amount > 0) { "결제 금액은 0보다 커야 합니다: $amount" }

            val now = Instant.now()
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
            createdAt: Instant,
            updatedAt: Instant,
        ): Payment = Payment(
            id, invoiceId, orderId, customerId, status, pgProvider,
            pgTransactionId, amount, paidAt, failReason, createdAt, updatedAt,
        )
    }

    fun markCompleted(pgTransactionId: String) {
        transitTo(PaymentStatus.COMPLETED)
        _pgTransactionId = pgTransactionId
        _paidAt = Instant.now()
    }

    fun markFailed(reason: String) {
        transitTo(PaymentStatus.FAILED)
        _failReason = reason
    }

    fun markRefunded() {
        transitTo(PaymentStatus.REFUNDED)
    }

    private fun transitTo(target: PaymentStatus) {
        checkState(_status.canTransitionTo(target)) {
            "결제 상태 전이가 유효하지 않습니다: $_status → $target"
        }
        _status = target
    }
}
