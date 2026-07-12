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
import org.slf4j.LoggerFactory
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

    private val log = LoggerFactory.getLogger(javaClass)

    /** InvoiceIssuedEvent 소비 진입점. */
    @Transactional
    fun chargeInvoice(invoiceId: Long) {
        val invoice = invoicePersistencePort.findById(invoiceId)
        if (invoice == null) {
            log.debug("자동과금 skip: 인보이스 없음 invoiceId={}", invoiceId)
            return
        }
        if (invoice.status !in setOf(InvoiceStatus.ISSUED, InvoiceStatus.OVERDUE)) {
            log.debug("자동과금 skip: 과금 대상 아닌 상태 invoiceId={} status={}", invoiceId, invoice.status)
            return
        }
        // 중복 이벤트 멱등: 이 인보이스의 결제가 이미 있으면 skip (재시도는 retryCharge 전용)
        if (paymentPersistencePort.findByOrderId(invoice.orderId)?.invoiceId == invoice.id) {
            log.debug("자동과금 skip: 이미 결제 존재(중복 이벤트) invoiceId={}", invoiceId)
            return
        }

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
            // PG 거절이 아니라 "고객이 결제수단을 등록/재등록해야 하는" 운영 신호 — 별도 메트릭으로 구분.
            log.warn("자동과금 실패: 활성 빌링키 없음(재등록 필요) customerId={} invoiceId={}", invoice.customerId, invoice.id)
            handleFailure(payment, "활성 빌링키 없음", isFirstAttempt, "carry.payment.autocharge.no_billing_key")
            return
        }
        val gateway = paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS)
        val result = try {
            gateway.chargeBilling(
                PgBillingChargeRequest(
                    billingKey = billingKey.billingKey,
                    customerKey = billingKey.customerKey,
                    orderId = invoice.orderId,
                    amount = invoice.totalAmount,
                    orderName = "세탁 서비스 (${invoice.weight}kg)",
                    idempotencyKey = "charge-${invoice.id}",
                ),
            )
        } catch (e: Exception) {
            // PG 호출 자체가 예외(타임아웃·CircuitBreaker OPEN·5xx)일 때 예외를 전파하면 @Transactional 이
            // markRetrying/scheduleRetry 를 롤백해 FAILED+과거 nextRetryAt 로 남고, 스위퍼가 백오프를
            // 건너뛰며 매 틱 재호출해 실패 중인 PG/카드를 두들긴다. handleFailure 로 흡수해 백오프 진전 상태를
            // 커밋한다(재시도는 결정적 멱등키로 PG 가 dedup).
            log.warn("자동과금 실패: PG 호출 예외 invoiceId={} paymentId={}", invoice.id, payment.id, e)
            handleFailure(payment, "PG 호출 예외: ${e.message}", isFirstAttempt)
            return
        }
        if (result.success) {
            val pgTransactionId = result.pgTransactionId
            if (pgTransactionId == null) {
                // success=true 인데 거래ID 가 없으면 완료 마킹이 불가능 — 실패로 처리해 재시도한다
                // (동일 멱등키 재호출을 PG 가 dedup 하므로 이중과금 위험 없음).
                log.error("자동과금 실패: PG 성공 응답에 거래ID 없음 invoiceId={}", invoice.id)
                handleFailure(payment, "PG 응답에 거래ID 없음", isFirstAttempt)
                return
            }
            payment.markCompleted(pgTransactionId, clock.instant())
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

    private fun handleFailure(
        payment: Payment,
        reason: String,
        isFirstAttempt: Boolean,
        metric: String = "carry.payment.autocharge.failed",
    ) {
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
        metricsPort.incrementCounter(metric)
    }
}
