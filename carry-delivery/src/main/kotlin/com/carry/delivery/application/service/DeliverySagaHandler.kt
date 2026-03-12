package com.carry.delivery.application.service

import com.carry.delivery.application.port.inbound.DeliverySagaEventHandler
import com.carry.delivery.application.port.outbound.DeliveryPersistencePort
import com.carry.delivery.domain.model.Delivery
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.order.OrderCancelledEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeliverySagaHandler(
    private val deliveryPersistencePort: DeliveryPersistencePort,
) : DeliverySagaEventHandler {

    @Transactional
    override fun onDispatchAccepted(event: DispatchAcceptedEvent) {
        val delivery = Delivery.create(
            orderId = event.orderId,
            dispatchId = event.dispatchId,
            carrierId = event.carrierId,
            laundromatId = event.laundromatId,
        )
        deliveryPersistencePort.save(delivery)
    }

    @Transactional
    override fun onOrderCancelled(event: OrderCancelledEvent) {
        val delivery = deliveryPersistencePort.findByOrderId(event.orderId) ?: return
        if (delivery.status.canTransitionTo(com.carry.delivery.domain.vo.DeliveryStatus.CANCELLED)) {
            delivery.cancel()
            deliveryPersistencePort.save(delivery)
        }
    }

    @Transactional
    override fun onDispatchCancelled(event: DispatchCancelledEvent) {
        val delivery = deliveryPersistencePort.findByOrderId(event.orderId) ?: return
        if (delivery.status.canTransitionTo(com.carry.delivery.domain.vo.DeliveryStatus.CANCELLED)) {
            delivery.cancel()
            deliveryPersistencePort.save(delivery)
        }
    }
}
