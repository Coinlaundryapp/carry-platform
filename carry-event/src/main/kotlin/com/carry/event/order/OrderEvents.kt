package com.carry.event.order

import java.time.Instant

data class OrderCreatedEvent(
    val orderId: Long,
    val customerId: Long,
    val laundromatId: Long,
    val laundryItemType: String,
    val selectedOptions: List<SelectedOptionDto>,
    val shippingAddress: ShippingAddressDto,
    val desiredPickupAt: Instant,
    val desiredDeliveryAt: Instant,
    val areaCode: String,
)

data class SelectedOptionDto(
    val optionType: String,
    val subOptionType: String,
)

data class ShippingAddressDto(
    val roadAddress: String,
    val detailAddress: String,
    val latitude: Double,
    val longitude: Double,
    val recipientName: String,
    val recipientPhone: String,
)

data class OrderCancelledEvent(
    val orderId: Long,
    val reason: String,
    val cancelledBy: String,
)
