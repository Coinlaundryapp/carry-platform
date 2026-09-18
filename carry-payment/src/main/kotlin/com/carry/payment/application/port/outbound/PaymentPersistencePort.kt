package com.carry.payment.application.port.outbound

import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import java.time.Instant

interface PaymentPersistencePort {
    fun save(payment: Payment): Payment
    fun findById(id: Long): Payment?
    fun findByOrderId(orderId: Long): Payment?

    /** 특정 상태의 결제 목록(예: 환불 재시도 스위퍼가 REFUND_PENDING 조회). */
    fun findByStatus(status: PaymentStatus): List<Payment>

    /** next_retry_at 이 도래한 FAILED 결제 목록(ChargeRetrySweeper 재과금 대상). */
    fun findRetryableFailed(now: Instant): List<Payment>

    /** PG 거래 ID 로 결제 조회(대사 잡의 PG-side 대조) — 재결제 이력이 있으면 최신 행. */
    fun findByPgTransactionId(pgTransactionId: String): Payment?

    /** 윈도 내 갱신된 provider별 특정 상태 결제 목록(대사 잡의 local-side 스캔). */
    fun findByProviderAndStatusInWindow(
        provider: PgProvider,
        statuses: Collection<PaymentStatus>,
        from: Instant,
        to: Instant,
    ): List<Payment>
}
