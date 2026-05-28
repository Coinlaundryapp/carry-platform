package com.carry.delivery.application.service

import com.carry.common.logging.SagaLogContext
import com.carry.delivery.application.port.inbound.DeliverySagaEventHandler
import com.carry.delivery.application.port.outbound.DeliveryPersistencePort
import com.carry.delivery.domain.model.Delivery
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.order.OrderCancelledEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeliverySagaHandler(
    private val deliveryPersistencePort: DeliveryPersistencePort,
) : DeliverySagaEventHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun onDispatchAccepted(event: DispatchAcceptedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Delivery saga: onDispatchAccepted dispatchId={} carrierId={}", event.dispatchId, event.carrierId)
            val delivery = Delivery.create(
                orderId = event.orderId,
                dispatchId = event.dispatchId,
                carrierId = event.carrierId,
                laundromatId = event.laundromatId,
            )
            deliveryPersistencePort.save(delivery)
        }
    }

    @Transactional
    override fun onOrderCancelled(event: OrderCancelledEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Delivery saga: onOrderCancelled reason={}", event.reason)
            val delivery = deliveryPersistencePort.findByOrderId(event.orderId) ?: return@withOrderId
            if (delivery.status.canTransitionTo(com.carry.delivery.domain.vo.DeliveryStatus.CANCELLED)) {
                delivery.cancel()
                deliveryPersistencePort.save(delivery)
            }
        }
    }

    @Transactional
    override fun onDispatchCancelled(event: DispatchCancelledEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Delivery saga: onDispatchCancelled dispatchId={} reason={}", event.dispatchId, event.reason)
            val delivery = deliveryPersistencePort.findByOrderId(event.orderId) ?: return@withOrderId
            if (delivery.status.canTransitionTo(com.carry.delivery.domain.vo.DeliveryStatus.CANCELLED)) {
                delivery.cancel()
                deliveryPersistencePort.save(delivery)
            }
        }
    }
}
