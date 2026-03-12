package com.carry.delivery.application.port.inbound

import com.carry.delivery.domain.model.Delivery

interface DeliveryQueryUseCase {
    fun getDelivery(deliveryId: Long): Delivery
    fun getDeliveryByOrder(orderId: Long): Delivery
    fun getDeliveriesByCarrier(carrierId: Long, cursor: Long?, size: Int): List<Delivery>
}
