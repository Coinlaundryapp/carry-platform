package com.carry.payment.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.payment.domain.model.ReconciliationMismatch
import com.carry.payment.domain.vo.ReconciliationMismatchType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant

/**
 * PG 대사 불일치 원장 — append-only 감지 기록.
 * (mismatchType, dedupKey) UNIQUE 로 재실행 중복 적재를 차단.
 * resolvedAt 은 운영 수동 처리용 표식(P1 코드 경로는 기록만, 갱신 없음).
 */
@Entity
@Table(name = "payment_reconciliation_mismatches")
class ReconciliationMismatchJpaEntity(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val mismatchType: ReconciliationMismatchType,

    @Column(nullable = false, length = 300)
    val dedupKey: String,

    val paymentId: Long?,

    val orderId: Long?,

    val pgTransactionId: String?,

    val localAmount: Long?,

    val pgAmount: Long?,

    @Column(nullable = false, length = 500)
    val detail: String,

    val resolvedAt: Instant? = null,
) : BaseEntity() {

    companion object {
        fun fromDomain(mismatch: ReconciliationMismatch): ReconciliationMismatchJpaEntity =
            ReconciliationMismatchJpaEntity(
                mismatchType = mismatch.type,
                dedupKey = mismatch.dedupKey,
                paymentId = mismatch.paymentId,
                orderId = mismatch.orderId,
                pgTransactionId = mismatch.pgTransactionId,
                localAmount = mismatch.localAmount,
                pgAmount = mismatch.pgAmount,
                detail = mismatch.detail,
            )
    }
}
