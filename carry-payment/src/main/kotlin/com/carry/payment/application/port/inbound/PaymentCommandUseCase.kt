package com.carry.payment.application.port.inbound

interface PaymentCommandUseCase {

    /**
     * 환불 의도를 표시한다(COMPLETED → REFUND_PENDING). PG 를 호출하지 않으므로 PG 장애와 무관하게
     * 항상 성공하며, 실제 환불은 [executeRefund](재시도 스위퍼)가 PG 복구 시 수행한다.
     * COMPLETED 가 아니면 무동작(멱등 — 선결제 없는 취소·중복 이벤트 안전).
     */
    fun markRefundPending(orderId: Long)

    /**
     * REFUND_PENDING 결제에 대해 PG 취소를 실행한다(성공 시 REFUNDED + RefundCompletedEvent).
     * PG 장애 시 예외를 던지며, 호출 측(재시도 스위퍼)이 REFUND_PENDING 을 유지해 다음 주기에 재시도한다.
     * REFUND_PENDING 이 아니면 무동작(멱등).
     */
    fun executeRefund(orderId: Long)

    /**
     * PG 측 취소가 이미 완료됐음이 확인된 REFUND_PENDING 결제를 PG 재호출 없이 REFUNDED 로
     * 수렴한다(REFUNDED 마킹 + RefundCompletedEvent — 환불 화해, PG 대사 잡 전용).
     * REFUND_PENDING 이 아니면 무동작(멱등).
     */
    fun confirmRefundFromPg(orderId: Long, refundAmount: Long)
}
