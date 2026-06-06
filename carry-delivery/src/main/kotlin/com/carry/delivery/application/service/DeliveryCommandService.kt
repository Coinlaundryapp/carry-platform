package com.carry.delivery.application.service

import com.carry.common.metrics.MetricsPort
import com.carry.delivery.application.port.inbound.DeliveryCommandUseCase
import com.carry.delivery.application.port.outbound.DeliveryPersistencePort
import com.carry.delivery.application.port.outbound.PaymentQueryPort
import com.carry.delivery.domain.exception.DeliveryNotFoundException
import com.carry.delivery.domain.exception.DeliveryNotOwnedException
import com.carry.delivery.domain.exception.OrderNotPaidException
import com.carry.delivery.domain.model.Delivery
import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.LaundryStartedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.delivery.SelectedOptionSnapshot
import com.carry.event.port.EventPublisherPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@Service
class DeliveryCommandService(
    private val deliveryPersistencePort: DeliveryPersistencePort,
    private val paymentQueryPort: PaymentQueryPort,
    private val eventPublisher: EventPublisherPort,
    private val metrics: MetricsPort,
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
        requestingCarrierId: Long,
    ): Delivery {
        val delivery = findOwnedDelivery(deliveryId, requestingCarrierId)
        delivery.completePickup(weight, photoIds)
        val saved = deliveryPersistencePort.save(delivery)

        eventPublisher.publish(
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
    override fun startWashing(deliveryId: Long, photoIds: List<Long>, requestingCarrierId: Long): Delivery {
        val delivery = findOwnedDelivery(deliveryId, requestingCarrierId)
        delivery.startWashing(photoIds)
        val saved = deliveryPersistencePort.save(delivery)

        eventPublisher.publish(
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
    override fun completeDrying(deliveryId: Long, photoIds: List<Long>, requestingCarrierId: Long): Delivery {
        val delivery = findOwnedDelivery(deliveryId, requestingCarrierId)
        delivery.completeDrying(photoIds)
        return deliveryPersistencePort.save(delivery)
    }

    @Transactional
    override fun startDelivery(deliveryId: Long, requestingCarrierId: Long): Delivery {
        val delivery = findOwnedDelivery(deliveryId, requestingCarrierId)
        delivery.startDelivery()
        return deliveryPersistencePort.save(delivery)
    }

    @Transactional
    override fun completeDelivery(deliveryId: Long, photoIds: List<Long>, requestingCarrierId: Long): Delivery {
        val delivery = findOwnedDelivery(deliveryId, requestingCarrierId)

        if (!paymentQueryPort.isOrderPaid(delivery.orderId)) {
            throw OrderNotPaidException(delivery.orderId)
        }

        delivery.completeDelivery(photoIds)
        val saved = deliveryPersistencePort.save(delivery)

        eventPublisher.publish(
            aggregateType = "Delivery",
            aggregateId = saved.id.toString(),
            eventType = "DeliveryCompletedEvent",
            payload = DeliveryCompletedEvent(
                deliveryId = saved.id!!,
                orderId = saved.orderId,
                carrierId = saved.carrierId,
            ),
        )

        // 배달 라이프사이클 길이 — Delivery aggregate 생성(=DispatchAccepted 사가 처리 시점)부터
        // 배달 완료까지. Clock 주입은 ROADMAP Phase 5에서 처리하므로 여기선 Instant.now() 직접 호출.
        metrics.incrementCounter("carry.delivery.completed")
        metrics.recordTimer("carry.delivery.duration", Duration.between(saved.createdAt, Instant.now()))

        return saved
    }

    private fun findDelivery(deliveryId: Long): Delivery {
        return deliveryPersistencePort.findById(deliveryId) ?: throw DeliveryNotFoundException(deliveryId)
    }

    // 배달 상태 변경은 그 배달에 배정된 캐리어 본인만 수행할 수 있다.
    private fun findOwnedDelivery(deliveryId: Long, requestingCarrierId: Long): Delivery {
        val delivery = findDelivery(deliveryId)
        if (delivery.carrierId != requestingCarrierId) {
            throw DeliveryNotOwnedException(deliveryId, requestingCarrierId)
        }
        return delivery
    }
}
