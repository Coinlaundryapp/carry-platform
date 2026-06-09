package com.carry.delivery.application.port.outbound.contract

import com.carry.delivery.application.port.outbound.PaymentQueryPort

class FakePaymentQueryPort : PaymentQueryPort {
    private val paidOrders = mutableSetOf<Long>()

    fun markPaid(orderId: Long) {
        paidOrders += orderId
    }

    override fun isOrderPaid(orderId: Long): Boolean = orderId in paidOrders
}
