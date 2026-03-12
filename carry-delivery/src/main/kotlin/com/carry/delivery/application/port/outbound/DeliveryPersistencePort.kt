package com.carry.delivery.application.port.outbound

import com.carry.delivery.domain.model.Delivery

interface DeliveryPersistencePort {
    fun save(delivery: Delivery): Delivery
    fun findById(id: Long): Delivery?
    fun findByOrderId(orderId: Long): Delivery?
    fun findByCarrierId(carrierId: Long): List<Delivery>
}
