package com.carry.order.application.service

import com.carry.common.logging.SagaLogContext
import com.carry.common.metrics.MetricsPort
import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.LaundryStartedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.dispatch.DispatchTimeoutEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.event.payment.PaymentFailedEvent
import com.carry.event.payment.RefundCompletedEvent
import com.carry.order.application.port.inbound.OrderSagaEventHandler
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.exception.OrderNotFoundException
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.CancelledBy
import com.carry.order.domain.vo.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

@Service
class OrderSagaHandler(
    private val orderPersistencePort: OrderPersistencePort,
    private val clock: Clock,
    private val metrics: MetricsPort,
) : OrderSagaEventHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun onDispatchAccepted(event: DispatchAcceptedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Order saga: onDispatchAccepted dispatchId={} carrierId={}", event.dispatchId, event.carrierId)
            val order = findOrder(event.orderId)
            if (skipIfForwardStopped(order, "DispatchAcceptedEvent")) return@withOrderId
            order.markDispatched(event.carrierId)
            orderPersistencePort.save(order)
        }
    }

    @Transactional
    override fun onDispatchTimeout(event: DispatchTimeoutEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Order saga: onDispatchTimeout dispatchId={}", event.dispatchId)
            val order = findOrder(event.orderId)
            if (order.isCancellable()) {
                order.cancel("배차 시간 초과", CancelledBy.SYSTEM, clock.instant())
                orderPersistencePort.save(order)
            }
        }
    }

    @Transactional
    override fun onDispatchCancelled(event: DispatchCancelledEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Order saga: onDispatchCancelled dispatchId={} reason={}", event.dispatchId, event.reason)
            val order = findOrder(event.orderId)
            if (order.isCancellable()) {
                order.cancel(event.reason, CancelledBy.COORDINATOR, clock.instant())
                orderPersistencePort.save(order)
            }
        }
    }

    @Transactional
    override fun onPickupCompleted(event: PickupCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Order saga: onPickupCompleted deliveryId={} weight={}", event.deliveryId, event.actualWeight)
            val order = findOrder(event.orderId)
            if (skipIfForwardStopped(order, "PickupCompletedEvent")) return@withOrderId
            order.markPickedUp(event.actualWeight)
            orderPersistencePort.save(order)
        }
    }

    @Transactional
    override fun onInvoiceIssued(event: InvoiceIssuedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Order saga: onInvoiceIssued invoiceId={} amount={}", event.invoiceId, event.totalAmount)
            val order = findOrder(event.orderId)
            if (skipIfForwardStopped(order, "InvoiceIssuedEvent")) return@withOrderId
            order.markInvoiced(event.invoiceId, event.totalAmount)
            orderPersistencePort.save(order)
        }
    }

    @Transactional
    override fun onPaymentCompleted(event: PaymentCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Order saga: onPaymentCompleted paymentId={}", event.paymentId)
            val order = findOrder(event.orderId)
            if (skipIfForwardStopped(order, "PaymentCompletedEvent")) return@withOrderId
            order.markPaid()
            orderPersistencePort.save(order)
        }
    }

    @Transactional
    override fun onPaymentFailed(event: PaymentFailedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.warn("Order saga: onPaymentFailed paymentId={} reason={}", event.paymentId, event.reason)
            val order = findOrder(event.orderId)
            // INVOICED 에서만 결제 실패로 전이 — 이미 PAID/취소/환불된 주문에 늦게 도착한 실패 이벤트는 무시(멱등/순서 안전).
            if (order.status == OrderStatus.INVOICED) {
                order.markPaymentFailed()
                orderPersistencePort.save(order)
            }
        }
    }

    @Transactional
    override fun onLaundryStarted(event: LaundryStartedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Order saga: onLaundryStarted deliveryId={}", event.deliveryId)
            val order = findOrder(event.orderId)
            if (skipIfForwardStopped(order, "LaundryStartedEvent")) return@withOrderId
            order.markInProgress()
            orderPersistencePort.save(order)
        }
    }

    @Transactional
    override fun onDeliveryCompleted(event: DeliveryCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Order saga: onDeliveryCompleted deliveryId={}", event.deliveryId)
            val order = findOrder(event.orderId)
            if (skipIfForwardStopped(order, "DeliveryCompletedEvent")) return@withOrderId
            val now = clock.instant()
            order.markCompleted(now)
            orderPersistencePort.save(order)
            // 사가 전체 소요시간 = 주문 생성(사가 시작)부터 배달 완료(사가 종료)까지 wall-clock.
            // 이벤트 스키마를 키우지 않고 애그리거트의 createdAt 을 활용한다(ADR-0005).
            metrics.recordTimer("carry.saga.duration", Duration.between(order.createdAt, now))
        }
    }

    @Transactional
    override fun onRefundCompleted(event: RefundCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Order saga: onRefundCompleted paymentId={} refundAmount={}", event.paymentId, event.refundAmount)
            val order = findOrder(event.orderId)
            // REFUND_PENDING 에서만 환불 완료로 전이(멱등 — 중복 RefundCompletedEvent 무시).
            if (order.status == OrderStatus.REFUND_PENDING) {
                order.markRefunded()
                orderPersistencePort.save(order)
            }
        }
    }

    /**
     * 취소·환불 분기·완료로 forward 진행이 중단된 주문에 늦게 도착한 forward 이벤트를
     * throw(→DLQ poison) 대신 멱등 no-op 으로 흡수한다(reverse 핸들러 가드와 대칭).
     * 선행 전이가 아직 반영되지 않은 "이른" 이벤트는 여기서 걸러지지 않고 도메인 가드에서
     * throw 되는데, 이는 의도적이다 — no-op 하면 전이가 영구 유실되므로 Kafka 재시도가 치유한다.
     */
    private fun skipIfForwardStopped(order: Order, eventType: String): Boolean {
        if (order.status.isForwardActive()) return false
        log.warn("Order saga: {} 무시 — forward 진행이 중단된 주문 orderId={} status={}", eventType, order.id, order.status)
        metrics.incrementCounter("carry.saga.forward_skipped", "event" to eventType, "status" to order.status.name)
        return true
    }

    private fun findOrder(orderId: Long): Order {
        return orderPersistencePort.findById(orderId) ?: throw OrderNotFoundException(orderId)
    }
}
