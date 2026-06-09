package com.carry.payment.domain.vo

enum class InvoiceStatus {
    ISSUED, PAID, CANCELLED, REFUNDED;

    fun canTransitionTo(target: InvoiceStatus): Boolean = when (this) {
        ISSUED -> target in listOf(PAID, CANCELLED)
        PAID -> target == REFUNDED
        CANCELLED -> false
        REFUNDED -> false
    }
}

enum class PaymentStatus {
    PENDING, COMPLETED, FAILED, REFUND_PENDING, REFUNDED;

    fun canTransitionTo(target: PaymentStatus): Boolean = when (this) {
        PENDING -> target in listOf(COMPLETED, FAILED)
        // 환불은 의도 표시(REFUND_PENDING) 후 PG 취소 성공 시 REFUNDED 로 — PG 장애 시 재시도 가능하게 단계 분리.
        COMPLETED -> target == REFUND_PENDING
        REFUND_PENDING -> target == REFUNDED
        FAILED -> target == PENDING
        REFUNDED -> false
    }
}

enum class PgProvider {
    TOSS_PAYMENTS,
}

enum class ChargeType {
    LAUNDRY_PRICE, DELIVERY_FEE, SERVICE_FEE,
}
