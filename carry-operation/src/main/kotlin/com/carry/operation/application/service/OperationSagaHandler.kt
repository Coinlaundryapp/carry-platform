package com.carry.operation.application.service

import com.carry.common.logging.SagaLogContext
import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.operation.application.port.inbound.OperationEventHandler
import com.carry.operation.application.port.outbound.OperationEventPersistencePort
import com.carry.operation.domain.model.OperationEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class OperationSagaHandler(
    private val operationEventPersistencePort: OperationEventPersistencePort,
    private val clock: Clock,
) : OperationEventHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun onOrderCreated(event: OrderCreatedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Operation saga: onOrderCreated laundromatId={}", event.laundromatId)
            val operationEvent = OperationEvent.create(
                eventType = "OrderCreatedEvent",
                aggregateType = "Order",
                aggregateId = event.orderId,
                summary = "주문 생성: orderId=${event.orderId}, laundromatId=${event.laundromatId}",
                now = clock.instant(),
            )
            operationEventPersistencePort.save(operationEvent)
        }
    }

    @Transactional
    override fun onOrderCancelled(event: OrderCancelledEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Operation saga: onOrderCancelled reason={}", event.reason)
            val operationEvent = OperationEvent.create(
                eventType = "OrderCancelledEvent",
                aggregateType = "Order",
                aggregateId = event.orderId,
                summary = "주문 취소: orderId=${event.orderId}, reason=${event.reason}",
                now = clock.instant(),
            )
            operationEventPersistencePort.save(operationEvent)
        }
    }

    @Transactional
    override fun onDispatchAccepted(event: DispatchAcceptedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Operation saga: onDispatchAccepted dispatchId={} carrierId={}", event.dispatchId, event.carrierId)
            val operationEvent = OperationEvent.create(
                eventType = "DispatchAcceptedEvent",
                aggregateType = "Dispatch",
                aggregateId = event.dispatchId,
                summary = "배차 수락: dispatchId=${event.dispatchId}, carrierId=${event.carrierId}",
                now = clock.instant(),
            )
            operationEventPersistencePort.save(operationEvent)
        }
    }

    @Transactional
    override fun onDeliveryCompleted(event: DeliveryCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Operation saga: onDeliveryCompleted deliveryId={}", event.deliveryId)
            val operationEvent = OperationEvent.create(
                eventType = "DeliveryCompletedEvent",
                aggregateType = "Delivery",
                aggregateId = event.deliveryId,
                summary = "배달 완료: deliveryId=${event.deliveryId}, orderId=${event.orderId}",
                now = clock.instant(),
            )
            operationEventPersistencePort.save(operationEvent)
        }
    }

    @Transactional
    override fun onPaymentCompleted(event: PaymentCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Operation saga: onPaymentCompleted paymentId={} amount={}", event.paymentId, event.amount)
            val operationEvent = OperationEvent.create(
                eventType = "PaymentCompletedEvent",
                aggregateType = "Payment",
                aggregateId = event.paymentId,
                summary = "결제 완료: paymentId=${event.paymentId}, amount=${event.amount}",
                now = clock.instant(),
            )
            operationEventPersistencePort.save(operationEvent)
        }
    }
}
