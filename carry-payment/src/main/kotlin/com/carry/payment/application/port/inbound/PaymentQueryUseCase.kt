package com.carry.payment.application.port.inbound

import com.carry.payment.domain.model.Payment

interface PaymentQueryUseCase {
    fun getPayment(paymentId: Long): Payment
    fun getPaymentByOrder(orderId: Long): Payment
    fun isOrderPaid(orderId: Long): Boolean
}
