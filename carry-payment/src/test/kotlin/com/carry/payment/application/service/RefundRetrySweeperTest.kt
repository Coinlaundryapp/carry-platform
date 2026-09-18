package com.carry.payment.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.payment.application.port.inbound.PaymentCommandUseCase
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.domain.exception.PaymentGatewayException
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class RefundRetrySweeperTest {

    private val paymentPersistencePort = mockk<PaymentPersistencePort>(relaxed = true)
    private val paymentCommandUseCase = mockk<PaymentCommandUseCase>(relaxed = true)
    private val metrics = mockk<MetricsPort>(relaxed = true)

    private val sut = RefundRetrySweeper(paymentPersistencePort, paymentCommandUseCase, metrics)

    private val now = Instant.parse("2026-06-10T00:00:00Z")

    private fun refundPending(orderId: Long) = Payment.reconstitute(
        id = orderId, invoiceId = orderId, orderId = orderId, customerId = 100L,
        status = PaymentStatus.REFUND_PENDING, pgProvider = PgProvider.TOSS_PAYMENTS,
        pgTransactionId = "tx_$orderId", amount = 18000L,
        paidAt = now, failReason = null, createdAt = now, updatedAt = now,
    )

    @Test
    fun `REFUND_PENDING 결제들에 대해 각각 executeRefund 를 호출한다`() {
        every { paymentPersistencePort.findByStatus(PaymentStatus.REFUND_PENDING) } returns
            listOf(refundPending(10L), refundPending(20L))

        sut.retryPendingRefunds()

        verify { paymentCommandUseCase.executeRefund(10L) }
        verify { paymentCommandUseCase.executeRefund(20L) }
    }

    @Test
    fun `대기 환불이 없으면 아무 것도 하지 않는다`() {
        every { paymentPersistencePort.findByStatus(PaymentStatus.REFUND_PENDING) } returns emptyList()

        sut.retryPendingRefunds()

        verify(exactly = 0) { paymentCommandUseCase.executeRefund(any()) }
    }

    @Test
    fun `한 환불이 PG 실패로 예외를 던져도 나머지를 계속 처리하고 실패 메트릭을 올린다`() {
        // PG CB OPEN 등으로 10L 환불 실패 → REFUND_PENDING 유지(다음 주기 재시도), 20L 은 계속 진행.
        every { paymentPersistencePort.findByStatus(PaymentStatus.REFUND_PENDING) } returns
            listOf(refundPending(10L), refundPending(20L))
        every { paymentCommandUseCase.executeRefund(10L) } throws PaymentGatewayException("circuit OPEN")

        sut.retryPendingRefunds()

        verify { paymentCommandUseCase.executeRefund(20L) }
        verify { metrics.incrementCounter("carry.payment.refund_retry_failed") }
    }
}
