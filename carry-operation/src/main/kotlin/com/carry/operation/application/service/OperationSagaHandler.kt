package com.carry.operation.application.service

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.operation.application.port.inbound.OperationEventHandler
import com.carry.operation.application.port.outbound.OperationEventPersistencePort
import com.carry.operation.domain.model.OperationEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OperationSagaHandler(
    private val operationEventPersistencePort: OperationEventPersistencePort,
) : OperationEventHandler {

    @Transactional
    override fun onOrderCreated(event: OrderCreatedEvent) {
        val operationEvent = OperationEvent.create(
            eventType = "OrderCreatedEvent",
            aggregateType = "Order",
            aggregateId = event.orderId,
            summary = "주문 생성: orderId=${event.orderId}, laundromatId=${event.laundromatId}",
        )
        operationEventPersistencePort.save(operationEvent)
    }

    @Transactional
    override fun onOrderCancelled(event: OrderCancelledEvent) {
        val operationEvent = OperationEvent.create(
            eventType = "OrderCancelledEvent",
            aggregateType = "Order",
            aggregateId = event.orderId,
            summary = "주문 취소: orderId=${event.orderId}, reason=${event.reason}",
        )
        operationEventPersistencePort.save(operationEvent)
    }

    @Transactional
    override fun onDispatchAccepted(event: DispatchAcceptedEvent) {
        val operationEvent = OperationEvent.create(
            eventType = "DispatchAcceptedEvent",
            aggregateType = "Dispatch",
            aggregateId = event.dispatchId,
            summary = "배차 수락: dispatchId=${event.dispatchId}, carrierId=${event.carrierId}",
        )
        operationEventPersistencePort.save(operationEvent)
    }

    @Transactional
    override fun onDeliveryCompleted(event: DeliveryCompletedEvent) {
        val operationEvent = OperationEvent.create(
            eventType = "DeliveryCompletedEvent",
            aggregateType = "Delivery",
            aggregateId = event.deliveryId,
            summary = "배달 완료: deliveryId=${event.deliveryId}, orderId=${event.orderId}",
        )
        operationEventPersistencePort.save(operationEvent)
    }

    @Transactional
    override fun onPaymentCompleted(event: PaymentCompletedEvent) {
        val operationEvent = OperationEvent.create(
            eventType = "PaymentCompletedEvent",
            aggregateType = "Payment",
            aggregateId = event.paymentId,
            summary = "결제 완료: paymentId=${event.paymentId}, amount=${event.amount}",
        )
        operationEventPersistencePort.save(operationEvent)
    }
}
