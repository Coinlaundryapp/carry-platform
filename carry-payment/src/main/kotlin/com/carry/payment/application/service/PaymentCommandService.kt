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
import com.carry.payment.application.port.outbound.LedgerPort
import com.carry.payment.application.port.outbound.OrderStateQueryPort
import com.carry.payment.application.port.outbound.PaymentIdempotencyPort
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.application.port.outbound.PaymentGatewayResolver
import com.carry.payment.application.port.outbound.PgPaymentRequest
import com.carry.payment.domain.exception.InvoiceAlreadyPaidException
import com.carry.payment.domain.exception.InvoiceNotFoundException
import com.carry.payment.domain.exception.PaymentGatewayException
import com.carry.payment.domain.exception.PaymentNotFoundException
import com.carry.payment.domain.model.LedgerEntries
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
    private val ledgerPort: LedgerPort,
    private val orderStateQueryPort: OrderStateQueryPort,
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

        // 선점(reserve) 이후 예외로 실패하면 키가 PENDING으로 남아 같은 키 재시도가 pendingTtl까지 409로
        // 막힌다. PG 예외(CB OPEN 등)는 전이성 오류라 재시도가 안전하므로, 예외 시 release로 즉시 해소한다.
        // (성공·정상실패는 아래에서 complete 후 return하므로 catch에 닿지 않는다.)
        try {
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

                // 정산 원장 기입 — 결제 확정과 동일 트랜잭션(유실 불가). 균형 기입은 팩토리가 강제.
                ledgerPort.record(
                    LedgerEntries.forPayment(saved, invoice, orderStateQueryPort.findCarrierId(command.orderId)),
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
                // 진짜 재시도는 새 키를 쓴다.
                key?.let { idempotencyPort.complete(it, saved.id!!) }
                metrics.incrementCounter("carry.payment.failure", "pg" to command.pgProvider.name)
                return saved
            }
        } catch (e: Exception) {
            // PG 예외 등 처리 실패 → 선점 해소(complete 미호출 경로)로 같은 키 즉시 재시도 가능.
            key?.let { idempotencyPort.release(it) }
            throw e
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
