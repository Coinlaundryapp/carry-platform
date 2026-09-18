package com.carry.payment.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.payment.domain.model.LedgerEntry
import com.carry.payment.domain.vo.ChargeType
import com.carry.payment.domain.vo.LedgerAccountType
import com.carry.payment.domain.vo.LedgerEntryType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

/** append-only 정산 원장 행 — 모든 필드 val, 수정 메서드 없음(역분개는 새 행 추가). */
@Entity
@Table(name = "payment_ledger_entries")
class LedgerEntryJpaEntity(
    @Column(nullable = false)
    val paymentId: Long,

    @Column(nullable = false)
    val orderId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val entryType: LedgerEntryType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val accountType: LedgerAccountType,

    val accountId: Long?,

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    val chargeType: ChargeType?,

    @Column(nullable = false)
    val amount: Long,
) : BaseEntity() {

    companion object {
        fun fromDomain(entry: LedgerEntry): LedgerEntryJpaEntity = LedgerEntryJpaEntity(
            paymentId = entry.paymentId,
            orderId = entry.orderId,
            entryType = entry.entryType,
            accountType = entry.accountType,
            accountId = entry.accountId,
            chargeType = entry.chargeType,
            amount = entry.amount,
        )
    }
}
