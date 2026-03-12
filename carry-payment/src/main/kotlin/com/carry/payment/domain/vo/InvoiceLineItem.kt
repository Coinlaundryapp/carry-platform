package com.carry.payment.domain.vo

data class InvoiceLineItem(
    val chargeType: ChargeType,
    val description: String,
    val amount: Long,
) {
    init {
        require(amount >= 0) { "금액은 0 이상이어야 합니다: $amount" }
    }
}
