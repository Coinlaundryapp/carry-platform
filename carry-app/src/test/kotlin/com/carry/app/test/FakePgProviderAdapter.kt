package com.carry.app.test

import com.carry.payment.application.port.outbound.PgCancelResult
import com.carry.payment.application.port.outbound.PgPaymentRequest
import com.carry.payment.application.port.outbound.PgPaymentResult
import com.carry.payment.application.port.outbound.PgProviderAdapter
import com.carry.payment.application.port.outbound.PgTransactionRecord
import com.carry.payment.application.port.outbound.PgTransactionType
import com.carry.payment.domain.vo.PgProvider
import org.springframework.boot.test.context.TestComponent
import java.time.Instant
import java.util.UUID

@TestComponent
class FakePgProviderAdapter : PgProviderAdapter {

    var shouldSucceed: Boolean = true
    var failReason: String = "결제 실패"

    /** 성공한 결제·취소가 자동 기록되는 "PG 측 원장". 대사 테스트에서 extraTransactions 로 고아 거래 주입 가능. */
    val recordedTransactions = mutableListOf<PgTransactionRecord>()
    val extraTransactions = mutableListOf<PgTransactionRecord>()

    override fun supports(): PgProvider = PgProvider.TOSS_PAYMENTS

    override fun requestPayment(request: PgPaymentRequest): PgPaymentResult {
        return if (shouldSucceed) {
            val pgTransactionId = "fake-txn-${UUID.randomUUID()}"
            recordedTransactions += PgTransactionRecord(
                pgTransactionId, PgTransactionType.CHARGE, request.amount, Instant.now(),
            )
            PgPaymentResult(
                success = true,
                pgTransactionId = pgTransactionId,
            )
        } else {
            PgPaymentResult(
                success = false,
                failReason = failReason,
            )
        }
    }

    override fun cancelPayment(pgTransactionId: String, idempotencyKey: String): PgCancelResult {
        return if (shouldSucceed) {
            val charge = recordedTransactions.firstOrNull {
                it.pgTransactionId == pgTransactionId && it.type == PgTransactionType.CHARGE
            }
            if (recordedTransactions.none { it.pgTransactionId == pgTransactionId && it.type == PgTransactionType.CANCEL }) {
                recordedTransactions += PgTransactionRecord(
                    pgTransactionId, PgTransactionType.CANCEL, charge?.amount ?: 0L, Instant.now(),
                )
            }
            PgCancelResult(success = true, refundAmount = charge?.amount)
        } else {
            PgCancelResult(success = false, failReason = failReason)
        }
    }

    override fun listTransactions(from: Instant, to: Instant): List<PgTransactionRecord> =
        (recordedTransactions + extraTransactions)
            .filter { !it.occurredAt.isBefore(from) && !it.occurredAt.isAfter(to) }

    fun reset() {
        shouldSucceed = true
        failReason = "결제 실패"
        recordedTransactions.clear()
        extraTransactions.clear()
    }
}
