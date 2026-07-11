package com.carry.app.adapter

import com.carry.order.application.port.inbound.OrderQueryUseCase
import com.carry.order.domain.exception.OrderNotFoundException
import com.carry.payment.application.port.outbound.OrderStateQueryPort
import org.springframework.stereotype.Component

/**
 * Cross-module adapter: carry-payment -> carry-order
 *
 * Implements the OrderStateQueryPort defined in carry-payment by delegating to carry-order's OrderQueryUseCase.
 */
@Component
class OrderStateQueryPortAdapter(
    private val orderQueryUseCase: OrderQueryUseCase,
) : OrderStateQueryPort {

    override fun isInvoiceable(orderId: Long): Boolean {
        return try {
            orderQueryUseCase.getOrderForCoordinator(orderId).status.isForwardActive()
        } catch (_: OrderNotFoundException) {
            // 없는 주문에 인보이스를 발행하면 고아 인보이스 — 발행 불가로 판정
            false
        }
    }
}
