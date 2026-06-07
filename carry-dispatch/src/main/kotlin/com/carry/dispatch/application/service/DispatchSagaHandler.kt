package com.carry.dispatch.application.service

import com.carry.common.logging.SagaLogContext
import com.carry.dispatch.application.port.inbound.DispatchSagaEventHandler
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.domain.model.Dispatch
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.port.EventPublisherPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DispatchSagaHandler(
    private val dispatchPersistencePort: DispatchPersistencePort,
    private val eventPublisher: EventPublisherPort,
) : DispatchSagaEventHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun onOrderCreated(event: OrderCreatedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Dispatch saga: onOrderCreated laundromatId={} areaCode={}", event.laundromatId, event.areaCode)
            val dispatch = Dispatch.create(
                orderId = event.orderId,
                laundromatId = event.laundromatId,
                areaCode = event.areaCode,
                desiredPickupAt = event.desiredPickupAt,
            )
            dispatchPersistencePort.save(dispatch)
        }
    }

    @Transactional
    override fun onOrderCancelled(event: OrderCancelledEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Dispatch saga: onOrderCancelled reason={}", event.reason)
            val dispatch = dispatchPersistencePort.findByOrderId(event.orderId) ?: return@withOrderId
            if (dispatch.status.canTransitionTo(com.carry.dispatch.domain.vo.DispatchStatus.CANCELLED)) {
                dispatch.cancel(event.reason)
                dispatchPersistencePort.save(dispatch)

                eventPublisher.publish(
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
}
