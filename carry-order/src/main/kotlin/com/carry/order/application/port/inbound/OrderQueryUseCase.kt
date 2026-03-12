package com.carry.order.application.port.inbound

import com.carry.order.domain.model.Order

interface OrderQueryUseCase {
    fun getOrder(orderId: Long): Order
    fun getOrdersByCustomer(customerId: Long, cursor: Long?, size: Int): List<Order>
}
