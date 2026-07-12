package com.carry.payment.domain.model

import com.carry.common.exception.BusinessException
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class PaymentTest {

    private val now = Instant.parse("2026-06-07T00:00:00Z")

    private fun createPayment() = Payment.create(
        invoiceId = 1L,
        orderId = 10L,
        customerId = 100L,
        pgProvider = PgProvider.TOSS_PAYMENTS,
        amount = 19500L,
        now = now,
    )

    private fun reconstitutedPayment(
        status: PaymentStatus = PaymentStatus.PENDING,
        retryCount: Int = 0,
        nextRetryAt: Instant? = null,
    ) = Payment.reconstitute(
        id = 1L, invoiceId = 1L, orderId = 10L, customerId = 100L,
        status = status, pgProvider = PgProvider.TOSS_PAYMENTS,
        pgTransactionId = if (status == PaymentStatus.COMPLETED) "tx_123" else null,
        amount = 19500L,
        paidAt = if (status == PaymentStatus.COMPLETED) now else null,
        failReason = null,
        retryCount = retryCount,
        nextRetryAt = nextRetryAt,
        createdAt = now, updatedAt = now,
    )

    @Nested
    inner class Create {

        @Test
        fun `결제를 생성하면 PENDING 상태이다`() {
            val payment = createPayment()
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(payment.id).isNull()
            assertThat(payment.pgTransactionId).isNull()
            assertThat(payment.createdAt).isEqualTo(now)
            assertThat(payment.updatedAt).isEqualTo(now)
        }

        @Test
        fun `결제 금액이 0 이하이면 예외가 발생한다`() {
            assertThatThrownBy {
                Payment.create(1L, 10L, 100L, PgProvider.TOSS_PAYMENTS, 0L, now)
            }.isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("결제 금액")
        }
    }

    @Nested
    inner class StateTransitions {

        @Test
        fun `PENDING 상태에서 markCompleted 호출 시 COMPLETED로 전이한다`() {
            val payment = reconstitutedPayment(PaymentStatus.PENDING)
            payment.markCompleted("tx_abc", now)
            assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETED)
            assertThat(payment.pgTransactionId).isEqualTo("tx_abc")
            assertThat(payment.paidAt).isEqualTo(now)
        }

        @Test
        fun `PENDING 상태에서 markFailed 호출 시 FAILED로 전이한다`() {
            val payment = reconstitutedPayment(PaymentStatus.PENDING)
            payment.markFailed("잔액 부족")
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failReason).isEqualTo("잔액 부족")
        }

        @Test
        fun `COMPLETED 에서 markRefundPending 으로 REFUND_PENDING 전이 후 markRefunded 로 REFUNDED 가 된다`() {
            val payment = reconstitutedPayment(PaymentStatus.COMPLETED)
            payment.markRefundPending()
            assertThat(payment.status).isEqualTo(PaymentStatus.REFUND_PENDING)
            payment.markRefunded()
            assertThat(payment.status).isEqualTo(PaymentStatus.REFUNDED)
        }

        @Test
        fun `COMPLETED 에서 markRefunded 직접 호출은 불가하다 - REFUND_PENDING 경유 필요`() {
            val payment = reconstitutedPayment(PaymentStatus.COMPLETED)
            assertThatThrownBy { payment.markRefunded() }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        fun `FAILED 상태에서 다시 PENDING으로 전이할 수 없다 - markCompleted 불가`() {
            val payment = reconstitutedPayment(PaymentStatus.FAILED)
            assertThatThrownBy { payment.markCompleted("tx_retry", now) }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        fun `COMPLETED 상태에서 markCompleted 호출 시 예외가 발생한다`() {
            val payment = reconstitutedPayment(PaymentStatus.COMPLETED)
            assertThatThrownBy { payment.markCompleted("tx_dup", now) }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        fun `REFUNDED 상태에서 어떤 전이도 불가하다`() {
            val payment = reconstitutedPayment(PaymentStatus.REFUNDED)
            assertThatThrownBy { payment.markCompleted("tx_x", now) }
                .isInstanceOf(BusinessException::class.java)
            assertThatThrownBy { payment.markFailed("reason") }
                .isInstanceOf(BusinessException::class.java)
            assertThatThrownBy { payment.markRefunded() }
                .isInstanceOf(BusinessException::class.java)
        }
    }

    @Nested
    inner class Retry {

        @Test
        fun `scheduleRetry 는 retryCount 증가 + nextRetryAt 설정`() {
            val payment = reconstitutedPayment(PaymentStatus.FAILED)
            payment.scheduleRetry(now)
            assertThat(payment.retryCount).isEqualTo(1)
            assertThat(payment.nextRetryAt).isEqualTo(now.plus(Duration.ofHours(1)))
        }

        @Test
        fun `scheduleRetry 는 FAILED 상태가 아니면 예외가 발생한다`() {
            val payment = reconstitutedPayment(PaymentStatus.PENDING)
            assertThatThrownBy { payment.scheduleRetry(now) }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        fun `markRetrying 은 FAILED 에서만 PENDING 재전이`() {
            val payment = reconstitutedPayment(PaymentStatus.FAILED, retryCount = 1, nextRetryAt = now)
            payment.markRetrying()
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(payment.nextRetryAt).isNull()
        }

        @Test
        fun `markRetrying 은 PENDING 상태에서 호출하면 예외가 발생한다`() {
            val payment = reconstitutedPayment(PaymentStatus.PENDING)
            assertThatThrownBy { payment.markRetrying() }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        fun `백오프 스케줄 - 1h, 4h, 12h, 24h 이후 24h 고정`() {
            assertThat(Payment.backoffFor(1)).isEqualTo(Duration.ofHours(1))
            assertThat(Payment.backoffFor(2)).isEqualTo(Duration.ofHours(4))
            assertThat(Payment.backoffFor(3)).isEqualTo(Duration.ofHours(12))
            assertThat(Payment.backoffFor(4)).isEqualTo(Duration.ofHours(24))
            assertThat(Payment.backoffFor(5)).isEqualTo(Duration.ofHours(24))
            assertThat(Payment.backoffFor(6)).isEqualTo(Duration.ofHours(24))
        }
    }
}
