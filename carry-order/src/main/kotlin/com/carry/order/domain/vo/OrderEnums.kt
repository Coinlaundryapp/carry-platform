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

    /**
     * forward 사가가 계속 진행 중인 상태인가.
     * 취소·환불 분기·완료로 빠진 주문은 false — 늦게 도착한 forward 이벤트를
     * throw(→DLQ) 대신 멱등 no-op 으로 처리할지 판정하는 술어.
     * PAYMENT_FAILED 는 재결제로 forward 재개가 가능하므로 활성으로 본다.
     */
    fun isForwardActive(): Boolean = this !in setOf(COMPLETED, REFUND_PENDING, REFUNDED, CANCELLED)
}

enum class CancelledBy {
    CUSTOMER,
    COORDINATOR,
    SYSTEM,
}
