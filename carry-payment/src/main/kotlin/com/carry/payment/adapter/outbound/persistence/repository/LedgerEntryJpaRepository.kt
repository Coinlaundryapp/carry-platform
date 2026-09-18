package com.carry.payment.adapter.outbound.persistence.repository

import com.carry.payment.adapter.outbound.persistence.entity.LedgerEntryJpaEntity
import com.carry.payment.domain.vo.LedgerAccountType
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

/**
 * append-only 원장이라 **노출하는 연산도 append-only** 로 제한한다.
 *
 * `JpaRepository` 를 상속하면 `save`·`delete*`·`deleteAll` 이 함께 열려, 관례로만 막혀 있던
 * 수정 경로가 오타 하나로 뚫린다(불변식 카탈로그 §6.2-4). 필요한 세 연산만 선언해
 * "쓰기는 추가뿐" 을 타입으로 강제하고, DB 쪽은 V31 트리거가 같은 규칙을 강제한다.
 */
interface LedgerEntryJpaRepository : Repository<LedgerEntryJpaEntity, Long> {

    fun saveAll(entities: Iterable<LedgerEntryJpaEntity>): List<LedgerEntryJpaEntity>

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
