package com.carry.payment.application.service

import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.domain.exception.PaymentNotFoundException
import com.carry.payment.domain.exception.PaymentNotOwnedException
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class PaymentQueryServiceTest {

    private val paymentPersistencePort = mockk<PaymentPersistencePort>()
    private val sut = PaymentQueryService(paymentPersistencePort)

    private val now = Instant.now()

    private fun aPayment(status: PaymentStatus = PaymentStatus.COMPLETED) = Payment.reconstitute(
        id = 1L, invoiceId = 1L, orderId = 10L, customerId = 100L,
        status = status, pgProvider = PgProvider.TOSS_PAYMENTS,
        pgTransactionId = "tx_123", amount = 18000L,
        paidAt = now, failReason = null, createdAt = now, updatedAt = now,
    )

    @Nested
    inner class GetPayment {

        @Test
        fun `존재하는 결제를 조회한다`() {
            every { paymentPersistencePort.findById(1L) } returns aPayment()

            val result = sut.getPayment(1L)
            assertThat(result.id).isEqualTo(1L)
        }

        @Test
        fun `존재하지 않는 결제 조회 시 예외가 발생한다`() {
            every { paymentPersistencePort.findById(999L) } returns null

            assertThatThrownBy { sut.getPayment(999L) }
                .isInstanceOf(PaymentNotFoundException::class.java)
        }
    }

    @Nested
    inner class GetPaymentByOrder {

        @Test
        fun `소유자가 주문 결제를 조회한다`() {
            every { paymentPersistencePort.findByOrderId(10L) } returns aPayment()

            val result = sut.getPaymentByOrder(10L, requestingUserId = 100L)
            assertThat(result.orderId).isEqualTo(10L)
        }

        @Test
        fun `다른 사용자가 조회하면 PaymentNotOwnedException 이 발생한다`() {
            every { paymentPersistencePort.findByOrderId(10L) } returns aPayment()

            assertThatThrownBy { sut.getPaymentByOrder(10L, requestingUserId = 999L) }
                .isInstanceOf(PaymentNotOwnedException::class.java)
        }
    }
}
