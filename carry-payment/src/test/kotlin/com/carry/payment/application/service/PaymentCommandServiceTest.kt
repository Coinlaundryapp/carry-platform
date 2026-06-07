package com.carry.payment.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.event.port.EventPublisherPort
import com.carry.payment.application.port.inbound.RequestPaymentCommand
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.application.port.outbound.PaymentGatewayPort
import com.carry.payment.application.port.outbound.PaymentGatewayResolver
import com.carry.payment.application.port.outbound.PgPaymentResult
import com.carry.payment.domain.exception.InvoiceAlreadyPaidException
import com.carry.payment.domain.exception.InvoiceNotFoundException
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

    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = PaymentCommandService(
        paymentPersistencePort, invoicePersistencePort, paymentGatewayResolver, eventPublisher, metrics, auditPort,
        clock,
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

    private fun aCommand() = RequestPaymentCommand(
        orderId = 10L,
        customerId = 100L,
        pgProvider = PgProvider.TOSS_PAYMENTS,
        paymentKey = "pk_test_123",
    )

    @Nested
    inner class RequestPayment {

        @Test
        fun `결제 요청 성공 시 COMPLETED 상태로 저장하고 이벤트를 발행한다`() {
            every { invoicePersistencePort.findByOrderId(10L) } returns anInvoice()
            every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
            every { paymentGateway.requestPayment(any()) } returns PgPaymentResult(
                success = true, pgTransactionId = "tx_success_123",
            )
            val saved = slot<Payment>()
            every { paymentPersistencePort.save(capture(saved)) } answers {
                Payment.reconstitute(
                    id = 42L, invoiceId = saved.captured.invoiceId, orderId = saved.captured.orderId,
                    customerId = saved.captured.customerId, status = saved.captured.status,
                    pgProvider = saved.captured.pgProvider, pgTransactionId = saved.captured.pgTransactionId,
                    amount = saved.captured.amount, paidAt = saved.captured.paidAt,
                    failReason = saved.captured.failReason, createdAt = now, updatedAt = now,
                )
            }

            val result = sut.requestPayment(aCommand())

            assertThat(result.id).isEqualTo(42L)
            assertThat(result.status).isEqualTo(PaymentStatus.COMPLETED)
            assertThat(result.pgTransactionId).isEqualTo("tx_success_123")
            verify { eventPublisher.publish("Payment", "10", "PaymentCompletedEvent", any(), any()) }
            verify { metrics.incrementCounter("carry.payment.success", "pg" to "TOSS_PAYMENTS") }
        }

        @Test
        fun `PG사 결제 실패 시 FAILED 상태로 저장하고 PaymentFailedEvent를 발행한다`() {
            every { invoicePersistencePort.findByOrderId(10L) } returns anInvoice()
            every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
            every { paymentGateway.requestPayment(any()) } returns PgPaymentResult(
                success = false, failReason = "잔액 부족",
            )
            val saved = slot<Payment>()
            every { paymentPersistencePort.save(capture(saved)) } answers {
                Payment.reconstitute(
                    id = 43L, invoiceId = saved.captured.invoiceId, orderId = saved.captured.orderId,
                    customerId = saved.captured.customerId, status = saved.captured.status,
                    pgProvider = saved.captured.pgProvider, pgTransactionId = saved.captured.pgTransactionId,
                    amount = saved.captured.amount, paidAt = saved.captured.paidAt,
                    failReason = saved.captured.failReason, createdAt = now, updatedAt = now,
                )
            }

            val result = sut.requestPayment(aCommand())

            assertThat(result.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(result.failReason).isEqualTo("잔액 부족")
            verify { eventPublisher.publish("Payment", "10", "PaymentFailedEvent", any(), any()) }
            verify { metrics.incrementCounter("carry.payment.failure", "pg" to "TOSS_PAYMENTS") }
        }

        @Test
        fun `청구서가 없으면 InvoiceNotFoundException이 발생한다`() {
            every { invoicePersistencePort.findByOrderId(10L) } returns null

            assertThatThrownBy { sut.requestPayment(aCommand()) }
                .isInstanceOf(InvoiceNotFoundException::class.java)
        }

        @Test
        fun `이미 결제된 청구서이면 InvoiceAlreadyPaidException이 발생한다`() {
            every { invoicePersistencePort.findByOrderId(10L) } returns anInvoice(InvoiceStatus.PAID)

            assertThatThrownBy { sut.requestPayment(aCommand()) }
                .isInstanceOf(InvoiceAlreadyPaidException::class.java)
        }
    }
}
