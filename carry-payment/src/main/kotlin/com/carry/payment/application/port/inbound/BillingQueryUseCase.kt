package com.carry.payment.application.port.inbound

/** carry-order 의 주문 생성 전제조건 조회 — carry-app 어댑터가 위임한다. */
interface BillingQueryUseCase {
    fun hasActiveBillingKey(customerId: Long): Boolean
    fun hasOverdueInvoice(customerId: Long): Boolean
}
