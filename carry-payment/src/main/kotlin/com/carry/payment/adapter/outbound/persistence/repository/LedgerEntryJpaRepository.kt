package com.carry.payment.adapter.outbound.persistence.repository

import com.carry.payment.adapter.outbound.persistence.entity.LedgerEntryJpaEntity
import com.carry.payment.domain.vo.LedgerAccountType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LedgerEntryJpaRepository : JpaRepository<LedgerEntryJpaEntity, Long> {

    @Query(
        "SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntryJpaEntity e " +
            "WHERE e.accountType = :accountType AND e.accountId = :accountId",
    )
    fun sumByAccount(
        @Param("accountType") accountType: LedgerAccountType,
        @Param("accountId") accountId: Long,
    ): Long

    @Query(
        "SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntryJpaEntity e " +
            "WHERE e.accountType = :accountType AND e.accountId IS NULL",
    )
    fun sumByAccountTypeWithoutId(@Param("accountType") accountType: LedgerAccountType): Long
}
