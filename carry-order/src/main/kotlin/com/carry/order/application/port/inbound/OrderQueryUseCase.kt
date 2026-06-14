package com.carry.order.application.port.inbound

import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.OrderStatus

interface OrderQueryUseCase {
    fun getOrder(orderId: Long, requestingUserId: Long): Order
    fun getOrdersByCustomer(customerId: Long, cursor: Long?, size: Int): List<Order>

    /** 코디네이터 운영용 — 소유자 검증 없이 전체 주문을 상태로 필터해 조회한다. */
    fun getOrdersForCoordinator(status: OrderStatus?, cursor: Long?, size: Int): List<Order>

    /** 코디네이터 운영용 — 소유자 검증 없이 단건 조회한다. */
    fun getOrderForCoordinator(orderId: Long): Order
}
