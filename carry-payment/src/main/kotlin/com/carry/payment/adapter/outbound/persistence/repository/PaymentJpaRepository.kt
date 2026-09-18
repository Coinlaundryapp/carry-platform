package com.carry.payment.adapter.outbound.persistence.repository

import com.carry.payment.adapter.outbound.persistence.entity.PaymentJpaEntity
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, Long> {
    // 재결제 시 주문당 결제 행이 여러 개(FAILED + COMPLETED) 생길 수 있으므로 최신 행("현재 결제")을 반환한다.
    // 단수 findByOrderId 는 다중 행에서 IncorrectResultSizeDataAccessException 을 던진다.
    fun findFirstByOrderIdOrderByIdDesc(orderId: Long): PaymentJpaEntity?

    fun findByStatus(status: PaymentStatus): List<PaymentJpaEntity>

    // ChargeRetrySweeper 대상 조회 — next_retry_at 이 도래한(과거인) FAILED 결제.
    fun findByStatusAndNextRetryAtBefore(status: PaymentStatus, before: Instant): List<PaymentJpaEntity>

    // pg_transaction_id 는 유니크 제약이 없으므로(재결제 이력) 최신 행을 반환한다.
    fun findFirstByPgTransactionIdOrderByIdDesc(pgTransactionId: String): PaymentJpaEntity?

    fun findByPgProviderAndStatusInAndUpdatedAtBetween(
        pgProvider: PgProvider,
        statuses: Collection<PaymentStatus>,
        from: Instant,
        to: Instant,
    ): List<PaymentJpaEntity>
}
