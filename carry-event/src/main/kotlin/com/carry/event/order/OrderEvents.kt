package com.carry.event.order

data class OrderCreatedEvent(
    val orderId: Long,
    val customerId: Long,
    val laundromatId: Long,
    val totalAmount: Long,
    val items: List<OrderItemSummary> = emptyList()
)

data class OrderItemSummary(
    val itemName: String,
    val quantity: Int,
    val price: Long
)

data class OrderPaidEvent(
    val orderId: Long,
    val customerId: Long,
    val totalAmount: Long
)

data class OrderCancelledEvent(
    val orderId: Long,
    val reason: String
)
