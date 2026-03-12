package com.carry.order.application.port.outbound

import com.carry.order.domain.model.Order

interface OrderPersistencePort {
    fun save(order: Order): Order
    fun findById(id: Long): Order?
    fun findByCustomerId(customerId: Long): List<Order>
}
