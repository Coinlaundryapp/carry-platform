package com.carry.payment.application.port.outbound

/**
 * Cross-module 조회 포트: carry-payment -> carry-order (어댑터는 carry-app).
 * 수거 완료 이벤트로 인보이스를 발행하기 전에 주문이 아직 인보이스를 받을 수 있는
 * 상태인지 확인한다 — 취소가 선커밋된 주문에 유령 인보이스가 발행되는 race 차단.
 */
interface OrderStateQueryPort {

    /** 취소·환불 분기·완료로 forward 진행이 중단된 주문이거나 주문이 없으면 false. */
    fun isInvoiceable(orderId: Long): Boolean
}
