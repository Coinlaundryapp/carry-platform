package com.carry.payment.adapter.outbound.stub

import com.carry.payment.application.port.outbound.PgPaymentRequest
import com.carry.payment.application.port.outbound.PgTransactionType
import com.carry.payment.domain.vo.PgProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StubPgProviderAdapterTest {

    private val now = Instant.parse("2026-07-12T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val adapter = StubPgProviderAdapter(clock)

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
        val result = adapter.cancelPayment("STUB-TX-42", "refund-1")

        assertThat(result.success).isTrue()
        assertThat(result.failReason).isNull()
    }

    @Test
    fun `결제 성공 시 CHARGE 거래가 원장에 기록된다`() {
        adapter.requestPayment(sampleRequest)

        val records = adapter.listTransactions(now.minusSeconds(60), now.plusSeconds(60))
        assertThat(records).hasSize(1)
        assertThat(records.single().type).isEqualTo(PgTransactionType.CHARGE)
        assertThat(records.single().pgTransactionId).isEqualTo("STUB-42-pk_test_abc")
        assertThat(records.single().amount).isEqualTo(15000L)
    }

    @Test
    fun `동일 결제키 재시도는 CHARGE 를 중복 기록하지 않는다`() {
        adapter.requestPayment(sampleRequest)
        adapter.requestPayment(sampleRequest)

        val records = adapter.listTransactions(now.minusSeconds(60), now.plusSeconds(60))
        assertThat(records).hasSize(1)
    }

    @Test
    fun `취소 시 원거래 금액으로 CANCEL 이 기록되고 refundAmount 를 반환한다`() {
        val txId = adapter.requestPayment(sampleRequest).pgTransactionId!!

        val result = adapter.cancelPayment(txId, "refund-1")

        assertThat(result.success).isTrue()
        assertThat(result.refundAmount).isEqualTo(15000L)
        val cancels = adapter.listTransactions(now.minusSeconds(60), now.plusSeconds(60))
            .filter { it.type == PgTransactionType.CANCEL }
        assertThat(cancels).hasSize(1)
        assertThat(cancels.single().amount).isEqualTo(15000L)
    }

    @Test
    fun `윈도 밖의 거래는 조회되지 않는다`() {
        adapter.requestPayment(sampleRequest)

        assertThat(adapter.listTransactions(now.plusSeconds(1), now.plusSeconds(60))).isEmpty()
        assertThat(adapter.listTransactions(now.minusSeconds(60), now.minusSeconds(1))).isEmpty()
    }
}
