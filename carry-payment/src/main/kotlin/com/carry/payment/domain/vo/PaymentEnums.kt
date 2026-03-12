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
    PENDING, COMPLETED, FAILED, REFUNDED;

    fun canTransitionTo(target: PaymentStatus): Boolean = when (this) {
        PENDING -> target in listOf(COMPLETED, FAILED)
        COMPLETED -> target == REFUNDED
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
