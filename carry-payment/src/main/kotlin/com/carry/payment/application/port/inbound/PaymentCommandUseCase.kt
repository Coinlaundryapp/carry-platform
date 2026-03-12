package com.carry.payment.application.port.inbound

import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PgProvider

interface PaymentCommandUseCase {
    fun requestPayment(command: RequestPaymentCommand): Payment
    fun requestRefund(orderId: Long, reason: String)
}

data class RequestPaymentCommand(
    val orderId: Long,
    val customerId: Long,
    val pgProvider: PgProvider,
    val paymentKey: String,
)
