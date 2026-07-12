package com.carry.payment.adapter.outbound.resilience

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.payment.application.port.outbound.PaymentGatewayPort
import com.carry.payment.application.port.outbound.PgBillingChargeRequest
import com.carry.payment.application.port.outbound.PgBillingKeyRequest
import com.carry.payment.application.port.outbound.PgBillingKeyResult
import com.carry.payment.application.port.outbound.PgPaymentResult
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

class CircuitBreakerPaymentGatewayTest {

    private val sampleBillingKeyRequest = PgBillingKeyRequest(
        authKey = "auth_test_xxx",
        customerKey = "cust_1",
    )

    private val sampleChargeRequest = PgBillingChargeRequest(
        billingKey = "billkey_1",
        customerKey = "cust_1",
        orderId = 1L,
        amount = 10000,
        orderName = "테스트 주문",
        idempotencyKey = "charge-1",
    )

    private fun circuitBreaker(windowSize: Int = 4, failureRate: Float = 50f): CircuitBreaker {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(windowSize)
            .minimumNumberOfCalls(windowSize)
            .failureRateThreshold(failureRate)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .build()
        return CircuitBreaker.of("test", config)
    }

    @Test
    fun `issueBillingKey 정상 호출은 delegate에 그대로 위임된다`() {
        val delegate = mockk<PaymentGatewayPort>()
        every { delegate.issueBillingKey(any()) } returns PgBillingKeyResult(success = true, billingKey = "billkey_1")
        val sut = CircuitBreakerPaymentGateway(delegate, circuitBreaker())

        val result = sut.issueBillingKey(sampleBillingKeyRequest)

        assertThat(result.success).isTrue()
        assertThat(result.billingKey).isEqualTo("billkey_1")
        verify(exactly = 1) { delegate.issueBillingKey(sampleBillingKeyRequest) }
    }

    @Test
    fun `chargeBilling 정상 호출은 delegate에 그대로 위임된다`() {
        val delegate = mockk<PaymentGatewayPort>()
        every { delegate.chargeBilling(any()) } returns PgPaymentResult(success = true, pgTransactionId = "tx-1")
        val sut = CircuitBreakerPaymentGateway(delegate, circuitBreaker())

        val result = sut.chargeBilling(sampleChargeRequest)

        assertThat(result.success).isTrue()
        assertThat(result.pgTransactionId).isEqualTo("tx-1")
        verify(exactly = 1) { delegate.chargeBilling(sampleChargeRequest) }
    }

    @Test
    fun `delegate가 던지는 예외는 그대로 전파된다 — circuit이 CLOSED일 때`() {
        val delegate = mockk<PaymentGatewayPort>()
        every { delegate.chargeBilling(any()) } throws IllegalStateException("PG returned 500")
        val sut = CircuitBreakerPaymentGateway(delegate, circuitBreaker())

        assertThatThrownBy { sut.chargeBilling(sampleChargeRequest) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("PG returned 500")
    }

    @Test
    fun `실패가 임계치를 넘으면 circuit이 OPEN되고 이후 호출은 BusinessException(PG_GATEWAY_UNAVAILABLE)로 fast-fail한다`() {
        val delegate = mockk<PaymentGatewayPort>()
        every { delegate.chargeBilling(any()) } throws RuntimeException("PG down")
        val cb = circuitBreaker(windowSize = 4, failureRate = 50f)
        val sut = CircuitBreakerPaymentGateway(delegate, cb)

        // 4회 연속 실패 → 실패율 100% → OPEN
        repeat(4) {
            assertThatThrownBy { sut.chargeBilling(sampleChargeRequest) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("PG down")
        }

        assertThat(cb.state).isEqualTo(CircuitBreaker.State.OPEN)

        // 다음 호출은 delegate에 도달하지 않고 즉시 BusinessException
        assertThatThrownBy { sut.chargeBilling(sampleChargeRequest) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode").isEqualTo(ErrorCode.PG_GATEWAY_UNAVAILABLE)

        verify(exactly = 4) { delegate.chargeBilling(any()) }
    }

    @Test
    fun `listTransactions는 delegate에 위임되고 circuit breaker로 보호된다`() {
        val delegate = mockk<PaymentGatewayPort>()
        every { delegate.listTransactions(any(), any()) } returns emptyList()
        val sut = CircuitBreakerPaymentGateway(delegate, circuitBreaker())
        val from = java.time.Instant.parse("2026-07-11T00:00:00Z")
        val to = java.time.Instant.parse("2026-07-12T00:00:00Z")

        val result = sut.listTransactions(from, to)

        assertThat(result).isEmpty()
        verify(exactly = 1) { delegate.listTransactions(from, to) }
    }

    @Test
    fun `listTransactions도 circuit OPEN 시 BusinessException으로 fast-fail한다`() {
        val delegate = mockk<PaymentGatewayPort>()
        every { delegate.listTransactions(any(), any()) } throws RuntimeException("PG down")
        val cb = circuitBreaker(windowSize = 4, failureRate = 50f)
        val sut = CircuitBreakerPaymentGateway(delegate, cb)
        val from = java.time.Instant.parse("2026-07-11T00:00:00Z")
        val to = java.time.Instant.parse("2026-07-12T00:00:00Z")

        repeat(4) {
            assertThatThrownBy { sut.listTransactions(from, to) }
                .isInstanceOf(RuntimeException::class.java)
        }

        assertThat(cb.state).isEqualTo(CircuitBreaker.State.OPEN)
        assertThatThrownBy { sut.listTransactions(from, to) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode").isEqualTo(ErrorCode.PG_GATEWAY_UNAVAILABLE)
    }

    @Test
    fun `cancelPayment도 circuit breaker로 보호된다`() {
        val delegate = mockk<PaymentGatewayPort>()
        every { delegate.cancelPayment(any(), any()) } throws RuntimeException("PG down")
        val cb = circuitBreaker(windowSize = 4, failureRate = 50f)
        val sut = CircuitBreakerPaymentGateway(delegate, cb)

        repeat(4) {
            assertThatThrownBy { sut.cancelPayment("tx-1", "refund-1") }
                .isInstanceOf(RuntimeException::class.java)
        }

        assertThat(cb.state).isEqualTo(CircuitBreaker.State.OPEN)
        assertThatThrownBy { sut.cancelPayment("tx-1", "refund-1") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode").isEqualTo(ErrorCode.PG_GATEWAY_UNAVAILABLE)
    }
}
