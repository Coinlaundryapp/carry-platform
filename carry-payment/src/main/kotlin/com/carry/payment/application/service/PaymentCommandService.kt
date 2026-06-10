package com.carry.payment.application.service

import com.carry.audit.domain.AuditAction
import com.carry.audit.port.AuditPort
import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.common.metrics.MetricsPort
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.event.payment.PaymentFailedEvent
import com.carry.event.payment.RefundCompletedEvent
import com.carry.event.port.EventPublisherPort
import com.carry.payment.application.port.inbound.PaymentCommandUseCase
import com.carry.payment.application.port.inbound.RequestPaymentCommand
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.application.port.outbound.PaymentIdempotencyPort
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.application.port.outbound.PaymentGatewayResolver
import com.carry.payment.application.port.outbound.PgPaymentRequest
import com.carry.payment.domain.exception.InvoiceAlreadyPaidException
import com.carry.payment.domain.exception.InvoiceNotFoundException
import com.carry.payment.domain.exception.PaymentGatewayException
import com.carry.payment.domain.exception.PaymentNotFoundException
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.InvoiceStatus
import com.carry.payment.domain.vo.PaymentStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class PaymentCommandService(
    private val paymentPersistencePort: PaymentPersistencePort,
    private val invoicePersistencePort: InvoicePersistencePort,
    private val paymentGatewayResolver: PaymentGatewayResolver,
    private val eventPublisher: EventPublisherPort,
    private val metrics: MetricsPort,
    private val auditPort: AuditPort,
    private val idempotencyPort: PaymentIdempotencyPort,
    private val clock: Clock,
) : PaymentCommandUseCase {

    @Transactional
    override fun requestPayment(command: RequestPaymentCommand): Payment {
        val key = command.idempotencyKey
        if (key != null) {
            // 이미 완료된 동일 키 → PG 재호출 없이 기존 결제(성공·실패 무관)를 재생.
            idempotencyPort.findCompletedPaymentId(key)?.let { return findPayment(it) }
            // 선점 실패 = 같은 키가 진행 중(또는 동시 요청 레이스의 패자) → 409.
            if (!idempotencyPort.reserve(key)) {
                throw BusinessException(
                    ErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS,
                    "동일한 Idempotency-Key 요청이 이미 진행 중입니다: $key",
                )
            }
        }

        val invoice = invoicePersistencePort.findByOrderId(command.orderId)
            ?: throw InvoiceNotFoundException("orderId=${command.orderId}")

        if (invoice.status != InvoiceStatus.ISSUED) {
            throw InvoiceAlreadyPaidException(invoice.id!!)
        }

        val payment = Payment.create(
            invoiceId = invoice.id!!,
            orderId = command.orderId,
            customerId = command.customerId,
            pgProvider = command.pgProvider,
            amount = invoice.totalAmount,
            now = clock.instant(),
        )

        val gateway = paymentGatewayResolver.resolve(command.pgProvider)
        val pgResult = gateway.requestPayment(
            PgPaymentRequest(
                orderId = command.orderId,
                amount = invoice.totalAmount,
                orderName = "캐리 세탁 주문 #${command.orderId}",
                customerName = "고객 #${command.customerId}",
                paymentKey = command.paymentKey,
            ),
        )

        if (pgResult.success && pgResult.pgTransactionId != null) {
            payment.markCompleted(pgResult.pgTransactionId!!, clock.instant())
            invoice.markPaid()
            invoicePersistencePort.save(invoice)

            val saved = paymentPersistencePort.save(payment)

            eventPublisher.publish(
                aggregateType = "Payment",
                aggregateId = command.orderId.toString(),
                eventType = "PaymentCompletedEvent",
                payload = PaymentCompletedEvent(
                    paymentId = saved.id!!,
                    orderId = saved.orderId,
                    invoiceId = saved.invoiceId,
                    amount = saved.amount,
                ),
            )

            key?.let { idempotencyPort.complete(it, saved.id!!) }
            metrics.incrementCounter("carry.payment.success", "pg" to command.pgProvider.name)
            return saved
        } else {
            payment.markFailed(pgResult.failReason ?: "알 수 없는 오류")
            val saved = paymentPersistencePort.save(payment)

            eventPublisher.publish(
                aggregateType = "Payment",
                aggregateId = command.orderId.toString(),
                eventType = "PaymentFailedEvent",
                payload = PaymentFailedEvent(
                    paymentId = saved.id!!,
                    orderId = saved.orderId,
                    reason = saved.failReason ?: "알 수 없는 오류",
                ),
            )

            // 정상 반환하는 FAILED 결과도 complete — 동일 키 재시도는 이 결과를 재생(같은 키=같은 작업).
            // 진짜 재시도는 새 키를 쓴다. PG '예외'(CB OPEN 등)는 트랜잭션 롤백 + complete 미호출 → pendingTtl 만료 후 재시도.
            key?.let { idempotencyPort.complete(it, saved.id!!) }
            metrics.incrementCounter("carry.payment.failure", "pg" to command.pgProvider.name)
            return saved
        }
    }

    private fun findPayment(id: Long): Payment =
        paymentPersistencePort.findById(id) ?: throw PaymentNotFoundException("id=$id")

    @Transactional
    override fun markRefundPending(orderId: Long) {
        val payment = paymentPersistencePort.findByOrderId(orderId)
            ?: throw PaymentNotFoundException("orderId=$orderId")

        // COMPLETED 일 때만 환불 대기로 전이. 선결제 없는 취소·중복 OrderCancelledEvent 는 무동작(멱등).
        if (payment.status != PaymentStatus.COMPLETED) return

        payment.markRefundPending()
        paymentPersistencePort.save(payment)
    }

    @Transactional
    override fun executeRefund(orderId: Long) {
        val payment = paymentPersistencePort.findByOrderId(orderId)
            ?: throw PaymentNotFoundException("orderId=$orderId")

        // REFUND_PENDING 만 실행 대상(멱등 — 이미 환불됐거나 대상 아님).
        if (payment.status != PaymentStatus.REFUND_PENDING) return

        val gateway = paymentGatewayResolver.resolve(payment.pgProvider)
        // PG CB OPEN/실패 시 예외가 전파된다 → 호출 측(RefundRetrySweeper)이 REFUND_PENDING 유지·다음 주기 재시도.
        val cancelResult = gateway.cancelPayment(payment.pgTransactionId!!)

        if (!cancelResult.success) {
            throw PaymentGatewayException(cancelResult.failReason ?: "환불 실패")
        }

        payment.markRefunded()
        val saved = paymentPersistencePort.save(payment)

        val invoice = invoicePersistencePort.findById(payment.invoiceId)
        invoice?.let {
            it.refund()
            invoicePersistencePort.save(it)
        }

        eventPublisher.publish(
            aggregateType = "Payment",
            aggregateId = orderId.toString(),
            eventType = "RefundCompletedEvent",
            payload = RefundCompletedEvent(
                paymentId = saved.id!!,
                orderId = saved.orderId,
                refundAmount = cancelResult.refundAmount ?: saved.amount,
            ),
        )

        auditPort.record(
            action = AuditAction.PAYMENT_REFUND,
            targetType = "PAYMENT",
            targetId = orderId.toString(),
            before = mapOf("status" to PaymentStatus.REFUND_PENDING.name),
            after = mapOf("status" to saved.status.name),
        )
    }
}
