package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.domain.vo.OrderShippingAddress

class FakeUserQueryPortContractTest : UserQueryPortContract() {

    private val fake = FakeUserQueryPort()

    override fun subject(): UserQueryPort = fake

    override fun arrangeAddress(userId: Long, addressId: Long, expected: OrderShippingAddress) {
        fake.put(userId, addressId, expected)
    }

    override fun arrangeMissing(userId: Long, addressId: Long) {
        // put 하지 않음
    }
}
