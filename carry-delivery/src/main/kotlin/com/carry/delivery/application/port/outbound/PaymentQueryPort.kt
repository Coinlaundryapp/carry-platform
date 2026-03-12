package com.carry.delivery.application.port.outbound

interface PaymentQueryPort {
    fun isOrderPaid(orderId: Long): Boolean
}
