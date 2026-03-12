package com.carry.app.test

import com.carry.payment.application.port.outbound.PgCancelResult
import com.carry.payment.application.port.outbound.PgPaymentRequest
import com.carry.payment.application.port.outbound.PgPaymentResult
import com.carry.payment.application.port.outbound.PgProviderAdapter
import com.carry.payment.domain.vo.PgProvider
import org.springframework.boot.test.context.TestComponent
import java.util.UUID

@TestComponent
class FakePgProviderAdapter : PgProviderAdapter {

    var shouldSucceed: Boolean = true
    var failReason: String = "결제 실패"

    override fun supports(): PgProvider = PgProvider.TOSS_PAYMENTS

    override fun requestPayment(request: PgPaymentRequest): PgPaymentResult {
        return if (shouldSucceed) {
            PgPaymentResult(
                success = true,
                pgTransactionId = "fake-txn-${UUID.randomUUID()}",
            )
        } else {
            PgPaymentResult(
                success = false,
                failReason = failReason,
            )
        }
    }

    override fun cancelPayment(pgTransactionId: String): PgCancelResult {
        return if (shouldSucceed) {
            PgCancelResult(success = true, refundAmount = null)
        } else {
            PgCancelResult(success = false, failReason = failReason)
        }
    }

    fun reset() {
        shouldSucceed = true
        failReason = "결제 실패"
    }
}
