package com.carry.payment.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.event.port.EventPublisherPort
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.application.port.outbound.LedgerPort
import com.carry.payment.application.port.outbound.OrderStateQueryPort
import com.carry.payment.application.port.outbound.PaymentIdempotencyPort
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.application.port.outbound.PaymentGatewayPort
import com.carry.payment.application.port.outbound.PaymentGatewayResolver
import com.carry.payment.application.port.outbound.PgCancelResult
import com.carry.payment.domain.exception.PaymentGatewayException
import com.carry.payment.domain.model.Invoice
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.ChargeType
import com.carry.payment.domain.vo.InvoiceLineItem
import com.carry.payment.domain.vo.InvoiceStatus
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PaymentCommandServiceTest {

    private val paymentPersistencePort = mockk<PaymentPersistencePort>(relaxed = true)
    private val invoicePersistencePort = mockk<InvoicePersistencePort>(relaxed = true)
    private val paymentGatewayResolver = mockk<PaymentGatewayResolver>()
    private val eventPublisher = mockk<EventPublisherPort>(relaxed = true)
    private val metrics = mockk<MetricsPort>(relaxed = true)
    private val paymentGateway = mockk<PaymentGatewayPort>()
    private val auditPort = mockk<com.carry.audit.port.AuditPort>(relaxed = true)
    private val idempotencyPort = mockk<PaymentIdempotencyPort>(relaxed = true)
    private val ledgerPort = mockk<LedgerPort>(relaxed = true)
    private val orderStateQueryPort = mockk<OrderStateQueryPort>(relaxed = true) {
        every { findCarrierId(any()) } returns 77L
    }

    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = PaymentCommandService(
        paymentPersistencePort, invoicePersistencePort, paymentGatewayResolver, eventPublisher, metrics, auditPort,
        idempotencyPort, ledgerPort, orderStateQueryPort, clock,
    )

    private val lineItems = listOf(
        InvoiceLineItem(ChargeType.LAUNDRY_PRICE, "세탁 비용", 15000L),
        InvoiceLineItem(ChargeType.DELIVERY_FEE, "배달비", 3000L),
    )

    private fun anInvoice(status: InvoiceStatus = InvoiceStatus.ISSUED) = Invoice.reconstitute(
        id = 1L, orderId = 10L, customerId = 100L, status = status,
        lineItems = lineItems, weight = BigDecimal("5.0"), totalAmount = 18000L,
        createdAt = now, updatedAt = now,
    )

    @Nested
    inner class Refund {

        private fun aPayment(status: PaymentStatus) = Payment.reconstitute(
            id = 1L, invoiceId = 1L, orderId = 10L, customerId = 100L, status = status,
            pgProvider = PgProvider.TOSS_PAYMENTS, pgTransactionId = "tx_123", amount = 18000L,
            paidAt = now, failReason = null, createdAt = now, updatedAt = now,
        )

        @Test
        fun `markRefundPending - COMPLETED 결제를 REFUND_PENDING 으로 표시하고 PG를 호출하지 않는다`() {
            every { paymentPersistencePort.findByOrderId(10L) } returns aPayment(PaymentStatus.COMPLETED)
            val saved = slot<Payment>()
            every { paymentPersistencePort.save(capture(saved)) } answers { saved.captured }

            sut.markRefundPending(10L)

            assertThat(saved.captured.status).isEqualTo(PaymentStatus.REFUND_PENDING)
            verify(exactly = 0) { paymentGatewayResolver.resolve(any()) }
        }

        @Test
        fun `markRefundPending - COMPLETED 가 아니면 무동작(멱등)`() {
            every { paymentPersistencePort.findByOrderId(10L) } returns aPayment(PaymentStatus.REFUND_PENDING)

            sut.markRefundPending(10L)

            verify(exactly = 0) { paymentPersistencePort.save(any()) }
        }

        @Test
        fun `executeRefund - REFUND_PENDING 결제를 PG 취소 성공 시 REFUNDED + RefundCompletedEvent`() {
            every { paymentPersistencePort.findByOrderId(10L) } returns aPayment(PaymentStatus.REFUND_PENDING)
            every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
            every { paymentGateway.cancelPayment("tx_123", any()) } returns PgCancelResult(success = true, refundAmount = 18000L)
            every { invoicePersistencePort.findById(1L) } returns anInvoice(InvoiceStatus.PAID)
            val saved = slot<Payment>()
            every { paymentPersistencePort.save(capture(saved)) } answers { saved.captured }

            sut.executeRefund(10L)

            assertThat(saved.captured.status).isEqualTo(PaymentStatus.REFUNDED)
            verify { eventPublisher.publish("Payment", "10", "RefundCompletedEvent", any(), any()) }
        }

        @Test
        fun `executeRefund - PG 취소에 결정적 멱등키(refund-paymentId)를 전달한다`() {
            // 스위퍼 재시도가 PG 측에서 dedup 되도록 — "PG 성공·로컬 마킹 실패" 윈도의 이중환불 차단
            every { paymentPersistencePort.findByOrderId(10L) } returns aPayment(PaymentStatus.REFUND_PENDING)
            every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
            every { paymentGateway.cancelPayment(any(), any()) } returns PgCancelResult(success = true, refundAmount = 18000L)
            every { invoicePersistencePort.findById(1L) } returns anInvoice(InvoiceStatus.PAID)
            every { paymentPersistencePort.save(any()) } answers { firstArg() }

            sut.executeRefund(10L)

            verify { paymentGateway.cancelPayment("tx_123", "refund-1") }
        }

        @Test
        fun `executeRefund - PG 취소 실패 시 예외를 던지고 환불 처리하지 않는다(스위퍼가 재시도)`() {
            every { paymentPersistencePort.findByOrderId(10L) } returns aPayment(PaymentStatus.REFUND_PENDING)
            every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
            every { paymentGateway.cancelPayment("tx_123", any()) } returns PgCancelResult(success = false, failReason = "PG 거절")

            assertThatThrownBy { sut.executeRefund(10L) }
                .isInstanceOf(PaymentGatewayException::class.java)

            verify(exactly = 0) { eventPublisher.publish(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `executeRefund - REFUND_PENDING 이 아니면 무동작(멱등)`() {
            every { paymentPersistencePort.findByOrderId(10L) } returns aPayment(PaymentStatus.COMPLETED)

            sut.executeRefund(10L)

            verify(exactly = 0) { paymentGatewayResolver.resolve(any()) }
        }

        @Test
        fun `confirmRefundFromPg - REFUND_PENDING 을 PG 재호출 없이 REFUNDED 로 수렴하고 이벤트를 발행한다`() {
            // 대사가 "PG 취소 완료·로컬 미반영" 을 발견한 경우 — PG 는 이미 취소됐으므로 로컬만 수렴
            every { paymentPersistencePort.findByOrderId(10L) } returns aPayment(PaymentStatus.REFUND_PENDING)
            every { invoicePersistencePort.findById(1L) } returns anInvoice(InvoiceStatus.PAID)
            val saved = slot<Payment>()
            every { paymentPersistencePort.save(capture(saved)) } answers { saved.captured }

            sut.confirmRefundFromPg(10L, 18000L)

            assertThat(saved.captured.status).isEqualTo(PaymentStatus.REFUNDED)
            verify { eventPublisher.publish("Payment", "10", "RefundCompletedEvent", any(), any()) }
            verify(exactly = 0) { paymentGatewayResolver.resolve(any()) }
        }

        @Test
        fun `confirmRefundFromPg - REFUND_PENDING 이 아니면 무동작(멱등)`() {
            every { paymentPersistencePort.findByOrderId(10L) } returns aPayment(PaymentStatus.REFUNDED)

            sut.confirmRefundFromPg(10L, 18000L)

            verify(exactly = 0) { paymentPersistencePort.save(any()) }
            verify(exactly = 0) { eventPublisher.publish(any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class Ledger {

        @Test
        fun `환불 확정 시 역분개(부호 반전 REFUND 그룹)를 기입한다`() {
            every { paymentPersistencePort.findByOrderId(10L) } returns Payment.reconstitute(
                id = 1L, invoiceId = 1L, orderId = 10L, customerId = 100L, status = PaymentStatus.REFUND_PENDING,
                pgProvider = PgProvider.TOSS_PAYMENTS, pgTransactionId = "tx_123", amount = 18000L,
                paidAt = now, failReason = null, createdAt = now, updatedAt = now,
            )
            every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
            every { paymentGateway.cancelPayment("tx_123", any()) } returns PgCancelResult(success = true, refundAmount = 18000L)
            every { invoicePersistencePort.findById(1L) } returns anInvoice(InvoiceStatus.PAID)
            every { paymentPersistencePort.save(any()) } answers { firstArg() }
            val entries = slot<List<com.carry.payment.domain.model.LedgerEntry>>()
            every { ledgerPort.record(capture(entries)) } returns Unit

            sut.executeRefund(10L)

            assertThat(entries.captured.sumOf { it.amount }).isZero()
            assertThat(entries.captured).allMatch { it.entryType == com.carry.payment.domain.vo.LedgerEntryType.REFUND }
            val customer = entries.captured.single { it.accountType == com.carry.payment.domain.vo.LedgerAccountType.CUSTOMER }
            assertThat(customer.amount).isEqualTo(+18000L)
        }
    }
}
