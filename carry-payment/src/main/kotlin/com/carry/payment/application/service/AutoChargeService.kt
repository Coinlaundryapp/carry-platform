package com.carry.payment.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.event.payment.PaymentFailedEvent
import com.carry.event.port.EventPublisherPort
import com.carry.payment.application.port.outbound.BillingKeyPersistencePort
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.application.port.outbound.LedgerPort
import com.carry.payment.application.port.outbound.OrderStateQueryPort
import com.carry.payment.application.port.outbound.PaymentGatewayResolver
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.application.port.outbound.PgBillingChargeRequest
import com.carry.payment.domain.model.Invoice
import com.carry.payment.domain.model.LedgerEntries
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.InvoiceStatus
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 빌링키 자동과금 사가. 물리 흐름(주문·배송)과 완전히 독립된 결제 사가의 시작점 —
 * [chargeInvoice]는 이 모듈이 자신이 발행한 InvoiceIssuedEvent 를 소비해 진입하고
 * (이 코드베이스 최초의 자체 소비 리스너, [PaymentSagaHandler] 참고),
 * [retryCharge]는 ChargeRetrySweeper(추후 태스크)가 백오프 스케줄에 맞춰 진입한다.
 */
@Service
class AutoChargeService(
    private val paymentPersistencePort: PaymentPersistencePort,
    private val invoicePersistencePort: InvoicePersistencePort,
    private val billingKeyPersistencePort: BillingKeyPersistencePort,
    private val paymentGatewayResolver: PaymentGatewayResolver,
    private val eventPublisher: EventPublisherPort,
    private val ledgerPort: LedgerPort,
    private val orderStateQueryPort: OrderStateQueryPort,
    private val metricsPort: MetricsPort,
    private val clock: Clock,
) {

    /** InvoiceIssuedEvent 소비 진입점. */
    @Transactional
    fun chargeInvoice(invoiceId: Long) {
        val invoice = invoicePersistencePort.findById(invoiceId) ?: return
        if (invoice.status !in setOf(InvoiceStatus.ISSUED, InvoiceStatus.OVERDUE)) return
        // 중복 이벤트 멱등: 이 인보이스의 결제가 이미 있으면 skip (재시도는 retryCharge 전용)
        if (paymentPersistencePort.findByOrderId(invoice.orderId)?.invoiceId == invoice.id) return

        val payment = Payment.create(
            invoice.id!!, invoice.orderId, invoice.customerId,
            PgProvider.TOSS_PAYMENTS, invoice.totalAmount, clock.instant(),
        )
        attemptCharge(paymentPersistencePort.save(payment), invoice, isFirstAttempt = true)
    }

    /** ChargeRetrySweeper 진입점. */
    @Transactional
    fun retryCharge(paymentId: Long) {
        val payment = paymentPersistencePort.findById(paymentId) ?: return
        if (payment.status != PaymentStatus.FAILED) return
        val invoice = invoicePersistencePort.findById(payment.invoiceId) ?: return
        // 취소된 인보이스는 재시도 제외 — next_retry_at 이 아니라 이 가드가 제외 기제다
        if (invoice.status !in setOf(InvoiceStatus.ISSUED, InvoiceStatus.OVERDUE)) return
        payment.markRetrying()
        attemptCharge(payment, invoice, isFirstAttempt = false)
    }

    private fun attemptCharge(payment: Payment, invoice: Invoice, isFirstAttempt: Boolean) {
        val billingKey = billingKeyPersistencePort.findActiveByCustomerId(invoice.customerId)
        if (billingKey == null) {
            handleFailure(payment, "활성 빌링키 없음", isFirstAttempt)
            return
        }
        val gateway = paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS)
        val result = gateway.chargeBilling(
            PgBillingChargeRequest(
                billingKey = billingKey.billingKey,
                customerKey = billingKey.customerKey,
                orderId = invoice.orderId,
                amount = invoice.totalAmount,
                orderName = "세탁 서비스 (${invoice.weight}kg)",
                idempotencyKey = "charge-${invoice.id}",
            ),
        )
        if (result.success) {
            payment.markCompleted(result.pgTransactionId!!, clock.instant())
            val saved = paymentPersistencePort.save(payment)
            invoice.markPaid()
            invoicePersistencePort.save(invoice)
            ledgerPort.record(
                LedgerEntries.forPayment(saved, invoice, orderStateQueryPort.findCarrierId(invoice.orderId)),
            )
            eventPublisher.publish(
                aggregateType = "Payment",
                aggregateId = invoice.orderId.toString(),
                eventType = "PaymentCompletedEvent",
                payload = PaymentCompletedEvent(saved.id!!, invoice.orderId, invoice.id!!, saved.amount),
            )
            metricsPort.incrementCounter("carry.payment.autocharge.success")
        } else {
            handleFailure(payment, result.failReason ?: "PG 과금 거절", isFirstAttempt)
        }
    }

    private fun handleFailure(payment: Payment, reason: String, isFirstAttempt: Boolean) {
        payment.markFailed(reason)
        payment.scheduleRetry(clock.instant())
        val saved = paymentPersistencePort.save(payment)
        // 알림 스팸 방지: 최초 실패만 이벤트 발행, 재시도 실패는 조용히 다음 예약만
        if (isFirstAttempt) {
            eventPublisher.publish(
                aggregateType = "Payment",
                aggregateId = saved.orderId.toString(),
                eventType = "PaymentFailedEvent",
                payload = PaymentFailedEvent(saved.id!!, saved.orderId, reason),
            )
        }
        metricsPort.incrementCounter("carry.payment.autocharge.failed")
    }
}
