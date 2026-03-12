package com.carry.event.delivery

import java.math.BigDecimal

data class PickupCompletedEvent(
    val deliveryId: Long,
    val orderId: Long,
    val carrierId: Long,
    val customerId: Long,
    val actualWeight: BigDecimal,
    val laundryItemType: String,
    val orderUnitType: String,
    val orderRequestType: String,
    val selectedOptions: List<SelectedOptionSnapshot>,
)

data class SelectedOptionSnapshot(
    val optionType: String,
    val subOptionType: String,
)

data class LaundryStartedEvent(
    val deliveryId: Long,
    val orderId: Long,
)

data class DeliveryCompletedEvent(
    val deliveryId: Long,
    val orderId: Long,
    val carrierId: Long,
)
