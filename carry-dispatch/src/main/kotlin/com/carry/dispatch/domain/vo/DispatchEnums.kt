package com.carry.dispatch.domain.vo

enum class DispatchStatus {
    PENDING,
    ASSIGNED,
    ACCEPTED,
    CANCELLED,
    TIMEOUT;

    fun canTransitionTo(target: DispatchStatus): Boolean = when (this) {
        PENDING -> target in setOf(ASSIGNED, ACCEPTED, CANCELLED, TIMEOUT)
        ASSIGNED -> target in setOf(ACCEPTED, PENDING, CANCELLED)
        ACCEPTED -> target == CANCELLED
        CANCELLED -> false
        TIMEOUT -> false
    }
}

enum class AssignedBy {
    CARRIER,
    COORDINATOR,
}

enum class PenaltyReason {
    REJECTED_FORCED_ASSIGNMENT,
}
