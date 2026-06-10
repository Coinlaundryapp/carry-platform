package com.carry.payment.application.port.inbound

import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PgProvider

interface PaymentCommandUseCase {
    fun requestPayment(command: RequestPaymentCommand): Payment

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
}

data class RequestPaymentCommand(
    val orderId: Long,
    val customerId: Long,
    val pgProvider: PgProvider,
    val paymentKey: String,
    val idempotencyKey: String? = null,
)
