package com.carry.order.domain.vo

enum class OrderStatus {
    CREATED,
    DISPATCHED,
    PICKED_UP,
    INVOICED,
    PAID,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    fun canTransitionTo(target: OrderStatus): Boolean = when (this) {
        CREATED -> target in setOf(DISPATCHED, CANCELLED)
        DISPATCHED -> target in setOf(PICKED_UP, CANCELLED)
        PICKED_UP -> target == INVOICED
        INVOICED -> target == PAID
        PAID -> target == IN_PROGRESS
        IN_PROGRESS -> target == COMPLETED
        COMPLETED -> false
        CANCELLED -> false
    }

    fun isCancellable(): Boolean = this in setOf(CREATED, DISPATCHED)
}

enum class CancelledBy {
    CUSTOMER,
    COORDINATOR,
    SYSTEM,
}
