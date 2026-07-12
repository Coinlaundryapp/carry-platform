package com.carry.payment.domain.model

import com.carry.common.exception.BusinessException
import com.carry.payment.domain.vo.ChargeType
import com.carry.payment.domain.vo.InvoiceLineItem
import com.carry.payment.domain.vo.InvoiceStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class InvoiceTest {

    private val lineItems = listOf(
        InvoiceLineItem(ChargeType.LAUNDRY_PRICE, "세탁 비용", 15000L),
        InvoiceLineItem(ChargeType.DELIVERY_FEE, "배달비", 3000L),
        InvoiceLineItem(ChargeType.SERVICE_FEE, "서비스 수수료", 1500L),
    )

    private val now = Instant.parse("2026-06-07T00:00:00Z")

    private fun createInvoice() = Invoice.create(
        orderId = 1L,
        customerId = 100L,
        lineItems = lineItems,
        weight = BigDecimal("5.0"),
        now = now,
    )

    private fun reconstitutedInvoice(status: InvoiceStatus = InvoiceStatus.ISSUED) = Invoice.reconstitute(
        id = 1L, orderId = 1L, customerId = 100L, status = status,
        lineItems = lineItems, weight = BigDecimal("5.0"), totalAmount = 19500L,
        createdAt = now, updatedAt = now,
    )

    @Nested
    inner class Create {

        @Test
        fun `청구서를 생성하면 ISSUED 상태이다`() {
            val invoice = createInvoice()
            assertThat(invoice.status).isEqualTo(InvoiceStatus.ISSUED)
            assertThat(invoice.id).isNull()
            assertThat(invoice.createdAt).isEqualTo(now)
            assertThat(invoice.updatedAt).isEqualTo(now)
        }

        @Test
        fun `청구서 생성 시 totalAmount는 lineItems 합계이다`() {
            val invoice = createInvoice()
            assertThat(invoice.totalAmount).isEqualTo(19500L)
        }

        @Test
        fun `lineItems가 비어 있으면 예외가 발생한다`() {
            assertThatThrownBy {
                Invoice.create(1L, 100L, emptyList(), BigDecimal("5.0"), now)
            }.isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("청구 항목")
        }

        @Test
        fun `lineItem 금액이 음수이면 예외가 발생한다`() {
            assertThatThrownBy {
                InvoiceLineItem(ChargeType.LAUNDRY_PRICE, "세탁 비용", -100L)
            }.isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("금액")
        }
    }

    @Nested
    inner class StateTransitions {

        @Test
        fun `ISSUED 상태에서 markPaid 호출 시 PAID로 전이한다`() {
            val invoice = reconstitutedInvoice(InvoiceStatus.ISSUED)
            invoice.markPaid()
            assertThat(invoice.status).isEqualTo(InvoiceStatus.PAID)
        }

        @Test
        fun `ISSUED 상태에서 cancel 호출 시 CANCELLED로 전이한다`() {
            val invoice = reconstitutedInvoice(InvoiceStatus.ISSUED)
            invoice.cancel()
            assertThat(invoice.status).isEqualTo(InvoiceStatus.CANCELLED)
        }

        @Test
        fun `PAID 상태에서 refund 호출 시 REFUNDED로 전이한다`() {
            val invoice = reconstitutedInvoice(InvoiceStatus.PAID)
            invoice.refund()
            assertThat(invoice.status).isEqualTo(InvoiceStatus.REFUNDED)
        }

        @Test
        fun `CANCELLED 상태에서 markPaid 호출 시 예외가 발생한다`() {
            val invoice = reconstitutedInvoice(InvoiceStatus.CANCELLED)
            assertThatThrownBy { invoice.markPaid() }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        fun `PAID 상태에서 cancel 호출 시 예외가 발생한다`() {
            val invoice = reconstitutedInvoice(InvoiceStatus.PAID)
            assertThatThrownBy { invoice.cancel() }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        fun `ISSUED 에서 OVERDUE 로 전이할 수 있다`() {
            val invoice = reconstitutedInvoice(InvoiceStatus.ISSUED)
            invoice.markOverdue()
            assertThat(invoice.status).isEqualTo(InvoiceStatus.OVERDUE)
        }

        @Test
        fun `OVERDUE 에서 PAID 로 전이할 수 있다`() {
            val invoice = reconstitutedInvoice(InvoiceStatus.OVERDUE)
            invoice.markPaid()
            assertThat(invoice.status).isEqualTo(InvoiceStatus.PAID)
        }

        @Test
        fun `OVERDUE 에서 CANCELLED 로 전이할 수 있다`() {
            val invoice = reconstitutedInvoice(InvoiceStatus.OVERDUE)
            invoice.cancel()
            assertThat(invoice.status).isEqualTo(InvoiceStatus.CANCELLED)
        }

        @Test
        fun `PAID 에서 OVERDUE 는 불가`() {
            val invoice = reconstitutedInvoice(InvoiceStatus.PAID)
            assertThatThrownBy { invoice.markOverdue() }
                .isInstanceOf(BusinessException::class.java)
        }
    }
}
