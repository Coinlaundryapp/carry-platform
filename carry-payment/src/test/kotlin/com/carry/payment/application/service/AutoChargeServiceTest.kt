package com.carry.payment.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.event.port.EventPublisherPort
import com.carry.payment.application.port.outbound.BillingKeyPersistencePort
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.application.port.outbound.LedgerPort
import com.carry.payment.application.port.outbound.OrderStateQueryPort
import com.carry.payment.application.port.outbound.PaymentGatewayPort
import com.carry.payment.application.port.outbound.PaymentGatewayResolver
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.application.port.outbound.PgBillingChargeRequest
import com.carry.payment.application.port.outbound.PgPaymentResult
import com.carry.payment.domain.model.BillingKey
import com.carry.payment.domain.model.Invoice
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.BillingKeyStatus
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
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AutoChargeServiceTest {

    private val paymentPersistencePort = mockk<PaymentPersistencePort>()
    private val invoicePersistencePort = mockk<InvoicePersistencePort>()
    private val billingKeyPersistencePort = mockk<BillingKeyPersistencePort>()
    private val paymentGatewayResolver = mockk<PaymentGatewayResolver>()
    private val paymentGateway = mockk<PaymentGatewayPort>()
    private val eventPublisher = mockk<EventPublisherPort>(relaxed = true)
    private val ledgerPort = mockk<LedgerPort>(relaxed = true)
    private val orderStateQueryPort = mockk<OrderStateQueryPort>(relaxed = true) {
        every { findCarrierId(any()) } returns 77L
    }
    private val metricsPort = mockk<MetricsPort>(relaxed = true)

    private val now = Instant.parse("2026-07-12T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val sut = AutoChargeService(
        paymentPersistencePort, invoicePersistencePort, billingKeyPersistencePort,
        paymentGatewayResolver, eventPublisher, ledgerPort, orderStateQueryPort, metricsPort, clock,
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

    private fun aBillingKey() = BillingKey.reconstitute(
        id = 1L, customerId = 100L, customerKey = "ck-1", billingKey = "bk-1",
        cardCompany = "토스카드", cardLast4 = "1234", status = BillingKeyStatus.ACTIVE,
        invalidatedAt = null, createdAt = now,
    )

    private fun aFailedPayment(retryCount: Int = 1) = Payment.reconstitute(
        id = 5L, invoiceId = 1L, orderId = 10L, customerId = 100L, status = PaymentStatus.FAILED,
        pgProvider = PgProvider.TOSS_PAYMENTS, pgTransactionId = null, amount = 18000L,
        paidAt = null, failReason = "PG 과금 거절", retryCount = retryCount, nextRetryAt = now,
        createdAt = now, updatedAt = now,
    )

    /**
     * save() 는 신규(id=null) 저장 시 id 를 부여한 새 인스턴스를 반환하고(실 어댑터의 toDomain 재조립을
     * 흉내), 기존(id 있음) 저장은 동일 인스턴스를 그대로 반환한다 — Payment 는 상태 필드가 var 라
     * attemptCharge 내부에서 반환된 인스턴스를 계속 in-place 로 변형해 재사용한다.
     * 반환하는 리스트는 각 save 호출의 인자를 순서대로 담아, 마지막 원소가 최종 저장 상태를 나타낸다.
     */
    private fun stubPaymentSaveAssignsId(): MutableList<Payment> {
        val captured = mutableListOf<Payment>()
        every { paymentPersistencePort.save(capture(captured)) } answers {
            val p = firstArg<Payment>()
            if (p.id == null) {
                Payment.reconstitute(
                    id = 5L, invoiceId = p.invoiceId, orderId = p.orderId, customerId = p.customerId,
                    status = p.status, pgProvider = p.pgProvider, pgTransactionId = p.pgTransactionId,
                    amount = p.amount, paidAt = p.paidAt, failReason = p.failReason,
                    retryCount = p.retryCount, nextRetryAt = p.nextRetryAt,
                    createdAt = p.createdAt, updatedAt = p.createdAt,
                )
            } else {
                p
            }
        }
        return captured
    }

    @Test
    fun `과금 성공 - Payment COMPLETED, Invoice PAID, 원장 기입, PaymentCompletedEvent 발행`() {
        val invoice = anInvoice(InvoiceStatus.ISSUED)
        every { invoicePersistencePort.findById(1L) } returns invoice
        every { paymentPersistencePort.findByOrderId(10L) } returns null
        val savedPayments = stubPaymentSaveAssignsId()
        val savedInvoice = slot<Invoice>()
        every { invoicePersistencePort.save(capture(savedInvoice)) } answers { firstArg() }
        every { billingKeyPersistencePort.findActiveByCustomerId(100L) } returns aBillingKey()
        every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
        every { paymentGateway.chargeBilling(any()) } returns PgPaymentResult(success = true, pgTransactionId = "tx-1")

        sut.chargeInvoice(1L)

        val finalPayment = savedPayments.last()
        assertThat(finalPayment.status).isEqualTo(PaymentStatus.COMPLETED)
        assertThat(finalPayment.pgTransactionId).isEqualTo("tx-1")
        assertThat(savedInvoice.captured.status).isEqualTo(InvoiceStatus.PAID)
        verify { ledgerPort.record(any()) }
        verify { eventPublisher.publish("Payment", "10", "PaymentCompletedEvent", any(), any()) }
        verify(exactly = 0) { eventPublisher.publish(any(), any(), "PaymentFailedEvent", any(), any()) }
        verify { metricsPort.incrementCounter("carry.payment.autocharge.success") }
    }

    @Test
    fun `과금 실패 - Payment FAILED, 재시도 예약, PaymentFailedEvent 발행(최초 1회)`() {
        val invoice = anInvoice(InvoiceStatus.ISSUED)
        every { invoicePersistencePort.findById(1L) } returns invoice
        every { paymentPersistencePort.findByOrderId(10L) } returns null
        val savedPayments = stubPaymentSaveAssignsId()
        every { billingKeyPersistencePort.findActiveByCustomerId(100L) } returns aBillingKey()
        every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
        every { paymentGateway.chargeBilling(any()) } returns PgPaymentResult(success = false, failReason = "잔액 부족")

        sut.chargeInvoice(1L)

        val finalPayment = savedPayments.last()
        assertThat(finalPayment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(finalPayment.retryCount).isEqualTo(1)
        assertThat(finalPayment.nextRetryAt).isNotNull()
        verify(exactly = 1) { eventPublisher.publish("Payment", "10", "PaymentFailedEvent", any(), any()) }
        verify(exactly = 0) { invoicePersistencePort.save(any()) }
    }

    @Test
    fun `활성 빌링키 없음 - PG 호출 없이 FAILED + 재시도 예약 + 이벤트 발행`() {
        val invoice = anInvoice(InvoiceStatus.ISSUED)
        every { invoicePersistencePort.findById(1L) } returns invoice
        every { paymentPersistencePort.findByOrderId(10L) } returns null
        val savedPayments = stubPaymentSaveAssignsId()
        every { billingKeyPersistencePort.findActiveByCustomerId(100L) } returns null

        sut.chargeInvoice(1L)

        verify(exactly = 0) { paymentGatewayResolver.resolve(any()) }
        val finalPayment = savedPayments.last()
        assertThat(finalPayment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(finalPayment.failReason).isEqualTo("활성 빌링키 없음")
        verify { eventPublisher.publish("Payment", "10", "PaymentFailedEvent", any(), any()) }
    }

    @Test
    fun `이미 결제가 존재하는 인보이스는 skip - 중복 이벤트 멱등`() {
        val invoice = anInvoice(InvoiceStatus.ISSUED)
        every { invoicePersistencePort.findById(1L) } returns invoice
        every { paymentPersistencePort.findByOrderId(10L) } returns aFailedPayment()

        sut.chargeInvoice(1L)

        verify(exactly = 0) { paymentPersistencePort.save(any()) }
        verify(exactly = 0) { billingKeyPersistencePort.findActiveByCustomerId(any()) }
        verify(exactly = 0) { eventPublisher.publish(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `ISSUED, OVERDUE 아닌 인보이스(CANCELLED)는 skip`() {
        val invoice = anInvoice(InvoiceStatus.CANCELLED)
        every { invoicePersistencePort.findById(1L) } returns invoice

        sut.chargeInvoice(1L)

        verify(exactly = 0) { paymentPersistencePort.findByOrderId(any()) }
        verify(exactly = 0) { paymentPersistencePort.save(any()) }
    }

    @Test
    fun `멱등키는 charge-invoiceId 로 PG 에 전달된다`() {
        val invoice = anInvoice(InvoiceStatus.ISSUED)
        every { invoicePersistencePort.findById(1L) } returns invoice
        every { paymentPersistencePort.findByOrderId(10L) } returns null
        stubPaymentSaveAssignsId()
        every { invoicePersistencePort.save(any()) } answers { firstArg() }
        every { billingKeyPersistencePort.findActiveByCustomerId(100L) } returns aBillingKey()
        every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
        val request = slot<PgBillingChargeRequest>()
        every { paymentGateway.chargeBilling(capture(request)) } returns PgPaymentResult(success = true, pgTransactionId = "tx-1")

        sut.chargeInvoice(1L)

        assertThat(request.captured.idempotencyKey).isEqualTo("charge-1")
    }

    @Test
    fun `재시도 성공 - FAILED 결제를 PENDING 재전이 후 COMPLETED, OVERDUE 인보이스는 PAID 회복`() {
        val invoice = anInvoice(InvoiceStatus.OVERDUE)
        every { paymentPersistencePort.findById(5L) } returns aFailedPayment()
        every { invoicePersistencePort.findById(1L) } returns invoice
        val savedPayments = stubPaymentSaveAssignsId()
        val savedInvoice = slot<Invoice>()
        every { invoicePersistencePort.save(capture(savedInvoice)) } answers { firstArg() }
        every { billingKeyPersistencePort.findActiveByCustomerId(100L) } returns aBillingKey()
        every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
        every { paymentGateway.chargeBilling(any()) } returns PgPaymentResult(success = true, pgTransactionId = "tx-2")

        sut.retryCharge(5L)

        assertThat(savedPayments.last().status).isEqualTo(PaymentStatus.COMPLETED)
        assertThat(savedInvoice.captured.status).isEqualTo(InvoiceStatus.PAID)
        verify { eventPublisher.publish("Payment", "10", "PaymentCompletedEvent", any(), any()) }
    }

    @Test
    fun `재시도 실패 - 이벤트 재발행 없이 다음 재시도만 예약`() {
        val invoice = anInvoice(InvoiceStatus.ISSUED)
        every { paymentPersistencePort.findById(5L) } returns aFailedPayment(retryCount = 1)
        every { invoicePersistencePort.findById(1L) } returns invoice
        val savedPayments = stubPaymentSaveAssignsId()
        every { billingKeyPersistencePort.findActiveByCustomerId(100L) } returns aBillingKey()
        every { paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS) } returns paymentGateway
        every { paymentGateway.chargeBilling(any()) } returns PgPaymentResult(success = false, failReason = "PG 거절")

        sut.retryCharge(5L)

        val finalPayment = savedPayments.last()
        assertThat(finalPayment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(finalPayment.retryCount).isEqualTo(2)
        verify(exactly = 0) { eventPublisher.publish(any(), any(), "PaymentFailedEvent", any(), any()) }
    }
}
