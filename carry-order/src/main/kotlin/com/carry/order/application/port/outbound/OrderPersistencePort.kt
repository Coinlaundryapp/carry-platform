package com.carry.order.application.port.outbound

import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.OrderStatus
import java.time.Instant

interface OrderPersistencePort {
    fun save(order: Order): Order
    fun findById(id: Long): Order?
    fun findByCustomerId(customerId: Long, cursor: Long?, size: Int): List<Order>
    fun findByStatusAndUpdatedAtBefore(status: OrderStatus, cutoff: Instant): List<Order>
}
