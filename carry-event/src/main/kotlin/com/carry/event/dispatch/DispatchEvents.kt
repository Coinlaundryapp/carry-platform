package com.carry.event.dispatch

data class DispatchAcceptedEvent(
    val dispatchId: Long,
    val orderId: Long,
    val carrierId: Long,
    val laundromatId: Long,
)

data class DispatchTimeoutEvent(
    val dispatchId: Long,
    val orderId: Long,
)

data class DispatchCancelledEvent(
    val dispatchId: Long,
    val orderId: Long,
    val reason: String,
)
