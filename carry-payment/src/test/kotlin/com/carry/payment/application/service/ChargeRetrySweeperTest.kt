package com.carry.payment.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ChargeRetrySweeperTest {

    private val paymentPersistencePort = mockk<PaymentPersistencePort>(relaxed = true)
    private val autoChargeService = mockk<AutoChargeService>(relaxed = true)
    private val metrics = mockk<MetricsPort>(relaxed = true)

    private val now = Instant.parse("2026-06-10T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = ChargeRetrySweeper(paymentPersistencePort, autoChargeService, metrics, clock)

    private fun retryableFailed(id: Long) = Payment.reconstitute(
        id = id, invoiceId = id, orderId = id, customerId = 100L,
        status = PaymentStatus.FAILED, pgProvider = PgProvider.TOSS_PAYMENTS,
        pgTransactionId = null, amount = 18000L,
        paidAt = null, failReason = "PG 과금 거절", retryCount = 1, nextRetryAt = now.minusSeconds(60),
        createdAt = now, updatedAt = now,
    )

    @Test
    fun `next_retry_at 도래한 FAILED 결제를 재과금한다`() {
        every { paymentPersistencePort.findRetryableFailed(now) } returns
            listOf(retryableFailed(10L), retryableFailed(20L))

        sut.retryFailedCharges()

        verify { autoChargeService.retryCharge(10L) }
        verify { autoChargeService.retryCharge(20L) }
    }

    @Test
    fun `개별 건 실패가 다른 건 처리를 막지 않는다`() {
        every { paymentPersistencePort.findRetryableFailed(now) } returns
            listOf(retryableFailed(10L), retryableFailed(20L))
        every { autoChargeService.retryCharge(10L) } throws RuntimeException("PG circuit OPEN")

        sut.retryFailedCharges()

        verify { autoChargeService.retryCharge(20L) }
        verify { metrics.incrementCounter("carry.payment.charge_retry_failed") }
    }

    @Test
    fun `도래하지 않은 건은 건드리지 않는다`() {
        every { paymentPersistencePort.findRetryableFailed(now) } returns emptyList()

        sut.retryFailedCharges()

        verify(exactly = 0) { autoChargeService.retryCharge(any()) }
    }
}
