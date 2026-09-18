package com.carry.payment.adapter.outbound.persistence.repository

import com.carry.payment.adapter.outbound.persistence.entity.InvoiceJpaEntity
import com.carry.payment.domain.vo.InvoiceStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface InvoiceJpaRepository : JpaRepository<InvoiceJpaEntity, Long> {
    fun findByOrderId(orderId: Long): InvoiceJpaEntity?

    // OverdueSweeper 대상 조회 — cutoff 이전에 발행된 ISSUED 인보이스.
    fun findByStatusAndCreatedAtBefore(status: InvoiceStatus, before: Instant): List<InvoiceJpaEntity>

    // 신규 주문 생성 전제조건 체크(고객에게 미수금 인보이스가 있는지).
    fun existsByCustomerIdAndStatus(customerId: Long, status: InvoiceStatus): Boolean

    /**
     * ISSUED 인 행만 OVERDUE 로 — 동시에 PAID 로 바뀐 인보이스를 덮어쓰지 않는 조건부 갱신
     * (ChargeRetrySweeper 와 OverdueSweeper 가 병행 실행되어도 lost-update 없이 안전).
     */
    @Modifying
    @Query(
        "update InvoiceJpaEntity i set i.status = com.carry.payment.domain.vo.InvoiceStatus.OVERDUE, " +
            "i.updatedAt = :now where i.id = :id and i.status = com.carry.payment.domain.vo.InvoiceStatus.ISSUED",
    )
    fun markOverdueIfIssued(@Param("id") id: Long, @Param("now") now: Instant): Int
}
