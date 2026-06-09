package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.domain.vo.OrderShippingAddress

class FakeUserQueryPort : UserQueryPort {
    private val store = mutableMapOf<Pair<Long, Long>, OrderShippingAddress>()

    fun put(userId: Long, addressId: Long, address: OrderShippingAddress) {
        store[userId to addressId] = address
    }

    override fun getShippingAddress(userId: Long, addressId: Long): OrderShippingAddress =
        store[userId to addressId]
            ?: throw NoSuchElementException("주소 없음: user=$userId, address=$addressId")
}
