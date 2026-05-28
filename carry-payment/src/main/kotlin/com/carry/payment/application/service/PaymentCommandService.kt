package com.carry.payment.application.service

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
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.application.port.outbound.PgPaymentRequest
import com.carry.payment.application.port.outbound.PgProviderRegistry
import com.carry.payment.domain.exception.InvoiceAlreadyPaidException
import com.carry.payment.domain.exception.InvoiceNotFoundException
import com.carry.payment.domain.exception.PaymentGatewayException
import com.carry.payment.domain.exception.PaymentNotFoundException
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.InvoiceStatus
import com.carry.payment.domain.vo.PaymentStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentCommandService(
    private val paymentPersistencePort: PaymentPersistencePort,
    private val invoicePersistencePort: InvoicePersistencePort,
    private val pgProviderRegistry: PgProviderRegistry,
    private val eventPublisher: EventPublisherPort,
    private val metrics: MetricsPort,
) : PaymentCommandUseCase {

    @Transactional
    override fun requestPayment(command: RequestPaymentCommand): Payment {
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
        )

        val gateway = pgProviderRegistry.resolve(command.pgProvider)
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
            payment.markCompleted(pgResult.pgTransactionId!!)
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

            metrics.incrementCounter("payment.completed.count")
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

            metrics.incrementCounter("payment.failed.count")
            return saved
        }
    }

    @Transactional
    override fun requestRefund(orderId: Long, reason: String) {
        val payment = paymentPersistencePort.findByOrderId(orderId)
            ?: throw PaymentNotFoundException("orderId=$orderId")

        if (payment.status != PaymentStatus.COMPLETED) {
            throw BusinessException(ErrorCode.PAYMENT_NOT_REFUNDABLE, "환불 가능한 상태가 아닙니다: ${payment.status}")
        }

        val gateway = pgProviderRegistry.resolve(payment.pgProvider)
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
    }
}
