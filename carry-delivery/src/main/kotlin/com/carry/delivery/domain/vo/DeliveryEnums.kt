package com.carry.delivery.domain.vo

enum class DeliveryStatus {
    PICKUP_PENDING,
    PICKED_UP,
    IN_LAUNDRY,
    LAUNDRY_COMPLETE,
    DELIVERY_PENDING,
    DELIVERED,
    CANCELLED;

    fun canTransitionTo(target: DeliveryStatus): Boolean = when (this) {
        PICKUP_PENDING -> target == PICKED_UP || target == CANCELLED
        PICKED_UP -> target == IN_LAUNDRY || target == CANCELLED
        IN_LAUNDRY -> target == LAUNDRY_COMPLETE || target == CANCELLED
        LAUNDRY_COMPLETE -> target == DELIVERY_PENDING || target == CANCELLED
        DELIVERY_PENDING -> target == DELIVERED || target == CANCELLED
        DELIVERED -> false
        CANCELLED -> false
    }
}

enum class DeliveryStepType {
    PICKUP,
    WEIGHING,
    WASHING,
    DRYING,
    DELIVERY,
}

enum class StepStatus {
    PENDING,
    COMPLETED,
}
