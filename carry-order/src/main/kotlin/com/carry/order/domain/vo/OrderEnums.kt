package com.carry.order.domain.vo

/** 주문 상태 — 물리 세계의 사실만 기술한다. 결제 생애주기는 carry-payment(Invoice/Payment) 소관. */
enum class OrderStatus {
    CREATED, DISPATCHED, PICKED_UP, IN_PROGRESS, COMPLETED, CANCELLED;

    fun canTransitionTo(target: OrderStatus): Boolean = when (this) {
        CREATED -> target in setOf(DISPATCHED, CANCELLED)
        DISPATCHED -> target in setOf(PICKED_UP, CANCELLED)
        PICKED_UP -> target in setOf(IN_PROGRESS, CANCELLED)
        IN_PROGRESS -> target in setOf(COMPLETED, CANCELLED)
        COMPLETED -> false
        CANCELLED -> false
    }

    /** 취소 가능 여부는 행위자에 따라 다르다 — 고객은 수거 전만, 코디/시스템은 완료 전까지. */
    fun isCancellableBy(by: CancelledBy): Boolean = when (by) {
        CancelledBy.CUSTOMER -> this in setOf(CREATED, DISPATCHED)
        CancelledBy.COORDINATOR, CancelledBy.SYSTEM ->
            this in setOf(CREATED, DISPATCHED, PICKED_UP, IN_PROGRESS)
    }

    /** forward 사가 진행 중 여부 — 늦게 도착한 이벤트의 멱등 no-op 판정 술어. */
    fun isForwardActive(): Boolean = this !in setOf(COMPLETED, CANCELLED)
}

enum class CancelledBy {
    CUSTOMER,
    COORDINATOR,
    SYSTEM,
}
