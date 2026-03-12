package com.carry.app.adapter

import com.carry.delivery.application.port.outbound.PaymentQueryPort
import com.carry.payment.application.port.inbound.PaymentQueryUseCase
import org.springframework.stereotype.Component

/**
 * Cross-module adapter: carry-delivery -> carry-payment
 *
 * Implements the PaymentQueryPort defined in carry-delivery by delegating to carry-payment's PaymentQueryUseCase.
 */
@Component
class PaymentQueryPortAdapter(
    private val paymentQueryUseCase: PaymentQueryUseCase,
) : PaymentQueryPort {

    override fun isOrderPaid(orderId: Long): Boolean {
        return paymentQueryUseCase.isOrderPaid(orderId)
    }
}
