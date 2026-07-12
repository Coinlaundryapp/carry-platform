package com.carry.payment.adapter.outbound.stub

import com.carry.payment.application.port.outbound.PgBillingChargeRequest
import com.carry.payment.application.port.outbound.PgBillingKeyRequest
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

    private val sampleBillingKeyRequest = PgBillingKeyRequest(
        authKey = "auth_test_abc",
        customerKey = "cust_7",
    )

    private val sampleChargeRequest = PgBillingChargeRequest(
        billingKey = "STUB-BILLKEY-cust_7",
        customerKey = "cust_7",
        orderId = 42L,
        amount = 15000,
        orderName = "캐리 세탁 주문 #42",
        idempotencyKey = "charge-42",
    )

    @Test
    fun `TOSS_PAYMENTS provider를 지원한다`() {
        assertThat(adapter.supports()).isEqualTo(PgProvider.TOSS_PAYMENTS)
    }

    @Test
    fun `빌링키 발급은 성공하고 결정적 billingKey를 반환한다`() {
        val result = adapter.issueBillingKey(sampleBillingKeyRequest)

        assertThat(result.success).isTrue()
        assertThat(result.billingKey).isEqualTo("STUB-BILLKEY-cust_7")
        assertThat(result.cardCompany).isNotBlank()
        assertThat(result.cardLast4).isNotBlank()
        assertThat(result.failReason).isNull()
    }

    @Test
    fun `authKey가 fail- 로 시작하면 빌링키 발급이 거절된다`() {
        val result = adapter.issueBillingKey(sampleBillingKeyRequest.copy(authKey = "fail-card"))

        assertThat(result.success).isFalse()
        assertThat(result.billingKey).isNull()
        assertThat(result.failReason).isNotBlank()
    }

    @Test
    fun `환불(취소) 요청은 항상 성공한다`() {
        val result = adapter.cancelPayment("STUB-TX-42", "refund-1")

        assertThat(result.success).isTrue()
        assertThat(result.failReason).isNull()
    }

    @Test
    fun `과금 성공 시 CHARGE 거래가 원장에 기록된다`() {
        val result = adapter.chargeBilling(sampleChargeRequest)

        assertThat(result.success).isTrue()
        assertThat(result.pgTransactionId).isNotBlank()

        val records = adapter.listTransactions(now.minusSeconds(60), now.plusSeconds(60))
        assertThat(records).hasSize(1)
        assertThat(records.single().type).isEqualTo(PgTransactionType.CHARGE)
        assertThat(records.single().amount).isEqualTo(15000L)
    }

    @Test
    fun `billingKey가 fail- 로 시작하면 과금이 거절되고 CHARGE 도 기록되지 않는다`() {
        val result = adapter.chargeBilling(sampleChargeRequest.copy(billingKey = "fail-billkey"))

        assertThat(result.success).isFalse()
        assertThat(result.pgTransactionId).isNull()
        assertThat(result.failReason).isNotBlank()
        assertThat(adapter.listTransactions(now.minusSeconds(60), now.plusSeconds(60))).isEmpty()
    }

    @Test
    fun `동일 멱등키 재과금은 CHARGE 를 중복 기록하지 않고 동일 결과를 재생한다`() {
        val first = adapter.chargeBilling(sampleChargeRequest)
        val second = adapter.chargeBilling(sampleChargeRequest)

        assertThat(first.pgTransactionId).isEqualTo(second.pgTransactionId)
        val records = adapter.listTransactions(now.minusSeconds(60), now.plusSeconds(60))
        assertThat(records).hasSize(1)
    }

    @Test
    fun `취소 시 원거래 금액으로 CANCEL 이 기록되고 refundAmount 를 반환한다`() {
        val txId = adapter.chargeBilling(sampleChargeRequest).pgTransactionId!!

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
        adapter.chargeBilling(sampleChargeRequest)

        assertThat(adapter.listTransactions(now.plusSeconds(1), now.plusSeconds(60))).isEmpty()
        assertThat(adapter.listTransactions(now.minusSeconds(60), now.minusSeconds(1))).isEmpty()
    }
}
