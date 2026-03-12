package com.carry.operation.domain.model

data class OperationSummary(
    val totalOrdersToday: Long,
    val pendingDispatches: Long,
    val activeDeliveries: Long,
    val completedToday: Long,
    val cancelledToday: Long,
)
