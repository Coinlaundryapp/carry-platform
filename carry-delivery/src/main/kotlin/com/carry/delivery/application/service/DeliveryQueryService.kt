package com.carry.delivery.application.service

import com.carry.delivery.application.port.inbound.DeliveryQueryUseCase
import com.carry.delivery.application.port.outbound.DeliveryPersistencePort
import com.carry.delivery.domain.exception.DeliveryNotFoundException
import com.carry.delivery.domain.model.Delivery
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class DeliveryQueryService(
    private val deliveryPersistencePort: DeliveryPersistencePort,
) : DeliveryQueryUseCase {

    override fun getDelivery(deliveryId: Long): Delivery {
        return deliveryPersistencePort.findById(deliveryId) ?: throw DeliveryNotFoundException(deliveryId)
    }

    override fun getDeliveryByOrder(orderId: Long): Delivery {
        return deliveryPersistencePort.findByOrderId(orderId)
            ?: throw DeliveryNotFoundException(orderId)
    }

    override fun getDeliveriesByCarrier(carrierId: Long): List<Delivery> {
        return deliveryPersistencePort.findByCarrierId(carrierId)
    }
}
