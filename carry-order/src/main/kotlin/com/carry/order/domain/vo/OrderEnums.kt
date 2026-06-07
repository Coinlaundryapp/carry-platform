package com.carry.order.domain.vo

enum class OrderStatus {
    CREATED,
    DISPATCHED,
    PICKED_UP,
    INVOICED,
    PAYMENT_FAILED,
    PAID,
    IN_PROGRESS,
    COMPLETED,
    REFUND_PENDING,
    REFUNDED,
    CANCELLED;

    fun canTransitionTo(target: OrderStatus): Boolean = when (this) {
        CREATED -> target in setOf(DISPATCHED, CANCELLED)
        DISPATCHED -> target in setOf(PICKED_UP, CANCELLED)
        PICKED_UP -> target == INVOICED
        INVOICED -> target in setOf(PAID, PAYMENT_FAILED)
        PAYMENT_FAILED -> target in setOf(PAID, CANCELLED)
        PAID -> target in setOf(IN_PROGRESS, REFUND_PENDING)
        IN_PROGRESS -> target == COMPLETED
        REFUND_PENDING -> target == REFUNDED
        COMPLETED -> false
        REFUNDED -> false
        CANCELLED -> false
    }

    fun isCancellable(): Boolean = this in setOf(CREATED, DISPATCHED, PAYMENT_FAILED)
}

enum class CancelledBy {
    CUSTOMER,
    COORDINATOR,
    SYSTEM,
}
