package com.carry.delivery.application.service

import com.carry.delivery.application.port.inbound.DeliveryCommandUseCase
import com.carry.delivery.application.port.outbound.DeliveryPersistencePort
import com.carry.delivery.application.port.outbound.PaymentQueryPort
import com.carry.delivery.domain.exception.DeliveryNotFoundException
import com.carry.delivery.domain.exception.OrderNotPaidException
import com.carry.delivery.domain.model.Delivery
import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.LaundryStartedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.delivery.SelectedOptionSnapshot
import com.carry.infra.kafka.outbox.OutboxEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class DeliveryCommandService(
    private val deliveryPersistencePort: DeliveryPersistencePort,
    private val paymentQueryPort: PaymentQueryPort,
    private val outboxEventPublisher: OutboxEventPublisher,
) : DeliveryCommandUseCase {

    @Transactional
    override fun completePickup(
        deliveryId: Long,
        weight: BigDecimal,
        photoIds: List<Long>,
        customerId: Long,
        laundryItemType: String,
        orderUnitType: String,
        orderRequestType: String,
        selectedOptions: List<SelectedOptionSnapshot>,
    ): Delivery {
        val delivery = findDelivery(deliveryId)
        delivery.completePickup(weight, photoIds)
        val saved = deliveryPersistencePort.save(delivery)

        outboxEventPublisher.publish(
            aggregateType = "Delivery",
            aggregateId = saved.id.toString(),
            eventType = "PickupCompletedEvent",
            payload = PickupCompletedEvent(
                deliveryId = saved.id!!,
                orderId = saved.orderId,
                carrierId = saved.carrierId,
                customerId = customerId,
                actualWeight = weight,
                laundryItemType = laundryItemType,
                orderUnitType = orderUnitType,
                orderRequestType = orderRequestType,
                selectedOptions = selectedOptions,
            ),
        )

        return saved
    }

    @Transactional
    override fun startWashing(deliveryId: Long, photoIds: List<Long>): Delivery {
        val delivery = findDelivery(deliveryId)
        delivery.startWashing(photoIds)
        val saved = deliveryPersistencePort.save(delivery)

        outboxEventPublisher.publish(
            aggregateType = "Delivery",
            aggregateId = saved.id.toString(),
            eventType = "LaundryStartedEvent",
            payload = LaundryStartedEvent(
                deliveryId = saved.id!!,
                orderId = saved.orderId,
            ),
        )

        return saved
    }

    @Transactional
    override fun completeDrying(deliveryId: Long, photoIds: List<Long>): Delivery {
        val delivery = findDelivery(deliveryId)
        delivery.completeDrying(photoIds)
        return deliveryPersistencePort.save(delivery)
    }

    @Transactional
    override fun startDelivery(deliveryId: Long): Delivery {
        val delivery = findDelivery(deliveryId)
        delivery.startDelivery()
        return deliveryPersistencePort.save(delivery)
    }

    @Transactional
    override fun completeDelivery(deliveryId: Long, photoIds: List<Long>): Delivery {
        val delivery = findDelivery(deliveryId)

        if (!paymentQueryPort.isOrderPaid(delivery.orderId)) {
            throw OrderNotPaidException(delivery.orderId)
        }

        delivery.completeDelivery(photoIds)
        val saved = deliveryPersistencePort.save(delivery)

        outboxEventPublisher.publish(
            aggregateType = "Delivery",
            aggregateId = saved.id.toString(),
            eventType = "DeliveryCompletedEvent",
            payload = DeliveryCompletedEvent(
                deliveryId = saved.id!!,
                orderId = saved.orderId,
                carrierId = saved.carrierId,
            ),
        )

        return saved
    }

    private fun findDelivery(deliveryId: Long): Delivery {
        return deliveryPersistencePort.findById(deliveryId) ?: throw DeliveryNotFoundException(deliveryId)
    }
}
