package com.carry.payment.application.service

import com.carry.audit.domain.AuditAction
import com.carry.audit.port.AuditPort
import com.carry.common.metrics.MetricsPort
import com.carry.event.payment.RefundCompletedEvent
import com.carry.event.port.EventPublisherPort
import com.carry.payment.application.port.inbound.PaymentCommandUseCase
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.application.port.outbound.LedgerPort
import com.carry.payment.application.port.outbound.OrderStateQueryPort
import com.carry.payment.application.port.outbound.PaymentIdempotencyPort
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.application.port.outbound.PaymentGatewayResolver
import com.carry.payment.domain.exception.PaymentGatewayException
import com.carry.payment.domain.exception.PaymentNotFoundException
import com.carry.payment.domain.model.LedgerEntries
import com.carry.payment.domain.model.Payment
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
    private val ledgerPort: LedgerPort,
    private val orderStateQueryPort: OrderStateQueryPort,
    private val clock: Clock,
) : PaymentCommandUseCase {

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
        // 멱등키는 결정적(전액 환불 1회 = refund-{paymentId}) — "PG 성공·로컬 마킹 실패" 후 재호출을 PG 가 dedup.
        // (부분환불(P4a) 도입 시 refund-seq 를 붙여 확장한다.)
        val cancelResult = gateway.cancelPayment(payment.pgTransactionId!!, "refund-${payment.id}")

        if (!cancelResult.success) {
            throw PaymentGatewayException(cancelResult.failReason ?: "환불 실패")
        }

        completeRefund(payment, cancelResult.refundAmount ?: payment.amount)
    }

    @Transactional
    override fun confirmRefundFromPg(orderId: Long, refundAmount: Long) {
        val payment = paymentPersistencePort.findByOrderId(orderId)
            ?: throw PaymentNotFoundException("orderId=$orderId")

        // REFUND_PENDING 만 수렴 대상(멱등 — 스위퍼와 경합해도 한쪽만 전이 성공).
        if (payment.status != PaymentStatus.REFUND_PENDING) return

        // PG 는 이미 취소를 완료했으므로(대사가 PG 원장에서 확인) PG 재호출 없이 로컬만 수렴.
        completeRefund(payment, if (refundAmount > 0) refundAmount else payment.amount)
    }

    /** PG 취소가 확정된 뒤의 로컬 마감 절반 — executeRefund(스위퍼)와 confirmRefundFromPg(대사 화해)가 공유. */
    private fun completeRefund(payment: Payment, refundAmount: Long) {
        payment.markRefunded()
        val saved = paymentPersistencePort.save(payment)

        val invoice = invoicePersistencePort.findById(payment.invoiceId)
        invoice?.let {
            it.refund()
            invoicePersistencePort.save(it)
            // 환불 확정과 동일 트랜잭션에서 원장 역분개(PAYMENT 그룹과 부호 반전).
            ledgerPort.record(
                LedgerEntries.forRefund(saved, it, orderStateQueryPort.findCarrierId(saved.orderId)),
            )
        }

        eventPublisher.publish(
            aggregateType = "Payment",
            aggregateId = saved.orderId.toString(),
            eventType = "RefundCompletedEvent",
            payload = RefundCompletedEvent(
                paymentId = saved.id!!,
                orderId = saved.orderId,
                refundAmount = refundAmount,
            ),
        )

        auditPort.record(
            action = AuditAction.PAYMENT_REFUND,
            targetType = "PAYMENT",
            targetId = saved.orderId.toString(),
            before = mapOf("status" to PaymentStatus.REFUND_PENDING.name),
            after = mapOf("status" to saved.status.name),
        )
    }
}
