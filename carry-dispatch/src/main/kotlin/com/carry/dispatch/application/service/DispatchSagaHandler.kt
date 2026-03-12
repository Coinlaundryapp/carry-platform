package com.carry.dispatch.application.service

import com.carry.dispatch.application.port.inbound.DispatchSagaEventHandler
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.domain.model.Dispatch
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.infra.kafka.outbox.OutboxEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DispatchSagaHandler(
    private val dispatchPersistencePort: DispatchPersistencePort,
    private val outboxEventPublisher: OutboxEventPublisher,
) : DispatchSagaEventHandler {

    @Transactional
    override fun onOrderCreated(event: OrderCreatedEvent) {
        val dispatch = Dispatch.create(
            orderId = event.orderId,
            laundromatId = event.laundromatId,
            areaCode = event.areaCode,
            desiredPickupAt = event.desiredPickupAt,
        )
        dispatchPersistencePort.save(dispatch)
    }

    @Transactional
    override fun onOrderCancelled(event: OrderCancelledEvent) {
        val dispatch = dispatchPersistencePort.findByOrderId(event.orderId) ?: return
        if (dispatch.status.canTransitionTo(com.carry.dispatch.domain.vo.DispatchStatus.CANCELLED)) {
            dispatch.cancel(event.reason)
            dispatchPersistencePort.save(dispatch)

            outboxEventPublisher.publish(
                aggregateType = "Dispatch",
                aggregateId = dispatch.orderId.toString(),
                eventType = "DispatchCancelledEvent",
                payload = DispatchCancelledEvent(
                    dispatchId = dispatch.id!!,
                    orderId = dispatch.orderId,
                    reason = event.reason,
                ),
            )
        }
    }
}
