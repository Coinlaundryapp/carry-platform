package com.carry.order.application.port.inbound

import com.carry.order.domain.model.Order

data class CreateOrderCommand(
    val customerId: Long,
    val shippingAddressId: Long,
    val laundromatId: Long,
    val laundryItemType: String,
    val selectedOptions: List<SelectedOptionCommand>,
    val desiredPickupAt: java.time.Instant,
    val desiredDeliveryAt: java.time.Instant,
    /** 선택적 멱등성 키(HTTP `Idempotency-Key` 헤더). 같으면 중복 생성 대신 기존 결과를 재생한다. */
    val idempotencyKey: String? = null,
)

data class SelectedOptionCommand(
    val optionType: String,
    val subOptionType: String,
)

interface OrderCommandUseCase {
    fun createOrder(command: CreateOrderCommand): Order
    fun cancelOrder(orderId: Long, reason: String, cancelledBy: String)
    fun cancelOrderByCustomer(orderId: Long, requestingUserId: Long, reason: String)
}
