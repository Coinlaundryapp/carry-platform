package com.carry.order.application.port.outbound

import com.carry.order.domain.vo.OrderShippingAddress

interface UserQueryPort {
    fun getShippingAddress(userId: Long, addressId: Long): OrderShippingAddress
}
