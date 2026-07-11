package com.carry.payment.domain.model

import com.carry.payment.domain.vo.ChargeType
import com.carry.payment.domain.vo.InvoiceLineItem
import com.carry.payment.domain.vo.InvoiceStatus
import com.carry.payment.domain.vo.LedgerAccountType
import com.carry.payment.domain.vo.LedgerEntryType
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class LedgerEntriesTest {

    private val now = Instant.parse("2026-07-12T00:00:00Z")

    private val payment = Payment.reconstitute(
        id = 42L, invoiceId = 1L, orderId = 10L, customerId = 100L, status = PaymentStatus.COMPLETED,
        pgProvider = PgProvider.TOSS_PAYMENTS, pgTransactionId = "tx-1", amount = 19500L,
        paidAt = now, failReason = null, createdAt = now, updatedAt = now,
    )

    private fun anInvoice(totalAmount: Long = 19500L) = Invoice.reconstitute(
        id = 1L, orderId = 10L, customerId = 100L, status = InvoiceStatus.PAID,
        lineItems = listOf(
            InvoiceLineItem(ChargeType.LAUNDRY_PRICE, "세탁 비용", 15000L),
            InvoiceLineItem(ChargeType.DELIVERY_FEE, "배달비", 3000L),
            InvoiceLineItem(ChargeType.SERVICE_FEE, "서비스 수수료", 1500L),
        ),
        weight = BigDecimal("5.0"), totalAmount = totalAmount, createdAt = now, updatedAt = now,
    )

    @Test
    fun `결제 그룹은 고객 차변 1행 + 라인아이템별 수취 행으로 구성되고 합계가 0이다`() {
        val entries = LedgerEntries.forPayment(payment, anInvoice(), carrierId = 77L)

        assertThat(entries).hasSize(4)
        assertThat(entries.sumOf { it.amount }).isZero()
        assertThat(entries).allMatch { it.entryType == LedgerEntryType.PAYMENT }

        val customer = entries.single { it.accountType == LedgerAccountType.CUSTOMER }
        assertThat(customer.amount).isEqualTo(-19500L)
        assertThat(customer.accountId).isEqualTo(100L)
        assertThat(customer.chargeType).isNull()
    }

    @Test
    fun `세탁비와 배달비는 캐리어에게, 수수료는 플랫폼에 귀속된다`() {
        // 세탁소는 수취인이 아님 — 캐리어가 코인세탁소 기계에 현금을 투입하므로 세탁비는 캐리어 변제
        val entries = LedgerEntries.forPayment(payment, anInvoice(), carrierId = 77L)

        val carrier = entries.filter { it.accountType == LedgerAccountType.CARRIER }
        assertThat(carrier.sumOf { it.amount }).isEqualTo(18000L) // 15000 + 3000
        assertThat(carrier).allMatch { it.accountId == 77L }
        assertThat(carrier.map { it.chargeType })
            .containsExactlyInAnyOrder(ChargeType.LAUNDRY_PRICE, ChargeType.DELIVERY_FEE)

        val platform = entries.single { it.accountType == LedgerAccountType.PLATFORM }
        assertThat(platform.amount).isEqualTo(1500L)
        assertThat(platform.accountId).isNull()
        assertThat(platform.chargeType).isEqualTo(ChargeType.SERVICE_FEE)
    }

    @Test
    fun `환불 그룹은 결제 그룹의 정확한 부호 반전이고 합계가 0이다`() {
        val paid = LedgerEntries.forPayment(payment, anInvoice(), carrierId = 77L)
        val refunded = LedgerEntries.forRefund(payment, anInvoice(), carrierId = 77L)

        assertThat(refunded.sumOf { it.amount }).isZero()
        assertThat(refunded).allMatch { it.entryType == LedgerEntryType.REFUND }
        // 결제+환불 전체 합도 계정별로 0 (원행 불변, 역분개로 상쇄)
        (paid + refunded).groupBy { it.accountType to it.accountId }.forEach { (_, group) ->
            assertThat(group.sumOf { it.amount }).isZero()
        }
    }

    @Test
    fun `인보이스 총액과 라인아이템 합계가 어긋나면 기입을 거부한다`() {
        // 균형(Σ=0) 불변식 — 산식 드리프트가 원장에 스며드는 것을 차단
        assertThatThrownBy { LedgerEntries.forPayment(payment, anInvoice(totalAmount = 20000L), carrierId = 77L) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("균형")
    }
}
