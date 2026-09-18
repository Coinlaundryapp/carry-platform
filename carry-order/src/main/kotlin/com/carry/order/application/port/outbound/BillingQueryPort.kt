package com.carry.order.application.port.outbound

/**
 * 주문 생성 전제조건 조회 (carry-payment 위임, carry-app 어댑터 배선).
 * 불변식: 존재하는 주문은 결제 때문에 멈추지 않는다 — 그 대가로 생성 시점에 지불수단을 확보한다.
 */
interface BillingQueryPort {
    fun hasActiveBillingKey(customerId: Long): Boolean
    fun hasOverdueInvoice(customerId: Long): Boolean
}
