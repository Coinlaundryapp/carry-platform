package com.carry.event.dispatch

data class DispatchCreatedEvent(
    val dispatchId: Long,
    val orderId: Long
)

data class DispatchAssignedEvent(
    val dispatchId: Long,
    val orderId: Long,
    val riderId: Long
)

data class DispatchFailedEvent(
    val dispatchId: Long,
    val orderId: Long,
    val reason: String
)

data class DeliveryCompletedEvent(
    val dispatchId: Long,
    val orderId: Long
)
