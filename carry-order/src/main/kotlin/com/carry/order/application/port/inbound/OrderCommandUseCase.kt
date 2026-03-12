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
)

data class SelectedOptionCommand(
    val optionType: String,
    val subOptionType: String,
)

interface OrderCommandUseCase {
    fun createOrder(command: CreateOrderCommand): Order
    fun cancelOrder(orderId: Long, reason: String, cancelledBy: String)
}
