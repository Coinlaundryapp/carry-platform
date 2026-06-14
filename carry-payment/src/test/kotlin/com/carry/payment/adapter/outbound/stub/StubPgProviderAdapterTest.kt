package com.carry.payment.adapter.outbound.stub

import com.carry.payment.application.port.outbound.PgPaymentRequest
import com.carry.payment.domain.vo.PgProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StubPgProviderAdapterTest {

    private val adapter = StubPgProviderAdapter()

    private val sampleRequest = PgPaymentRequest(
        orderId = 42L,
        amount = 15000,
        orderName = "캐리 세탁 주문 #42",
        customerName = "고객 #7",
        paymentKey = "pk_test_abc",
    )

    @Test
    fun `TOSS_PAYMENTS provider를 지원한다`() {
        assertThat(adapter.supports()).isEqualTo(PgProvider.TOSS_PAYMENTS)
    }

    @Test
    fun `결제 요청은 항상 성공하고 결정적 거래 ID를 반환한다`() {
        val result = adapter.requestPayment(sampleRequest)

        assertThat(result.success).isTrue()
        assertThat(result.pgTransactionId).isNotBlank()
        assertThat(result.failReason).isNull()
    }

    @Test
    fun `동일 요청은 동일 거래 ID를 재생한다 (결정적)`() {
        val first = adapter.requestPayment(sampleRequest)
        val second = adapter.requestPayment(sampleRequest)

        assertThat(first.pgTransactionId).isEqualTo(second.pgTransactionId)
    }

    @Test
    fun `환불(취소) 요청은 항상 성공한다`() {
        val result = adapter.cancelPayment("STUB-TX-42")

        assertThat(result.success).isTrue()
        assertThat(result.failReason).isNull()
    }
}
