package com.carry.order.application.service

import com.carry.order.application.port.inbound.OrderQueryUseCase
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.exception.OrderNotFoundException
import com.carry.order.domain.model.Order
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class OrderQueryService(
    private val orderPersistencePort: OrderPersistencePort,
) : OrderQueryUseCase {

    override fun getOrder(orderId: Long): Order {
        return orderPersistencePort.findById(orderId) ?: throw OrderNotFoundException(orderId)
    }

    override fun getOrdersByCustomer(customerId: Long, cursor: Long?, size: Int): List<Order> {
        return orderPersistencePort.findByCustomerId(customerId, cursor, size)
    }
}
