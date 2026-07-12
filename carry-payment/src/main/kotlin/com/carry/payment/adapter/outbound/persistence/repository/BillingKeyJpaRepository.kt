package com.carry.payment.adapter.outbound.persistence.repository

import com.carry.payment.adapter.outbound.persistence.entity.BillingKeyJpaEntity
import com.carry.payment.domain.vo.BillingKeyStatus
import org.springframework.data.jpa.repository.JpaRepository

interface BillingKeyJpaRepository : JpaRepository<BillingKeyJpaEntity, Long> {
    // 고객당 활성 키는 1개(DB 부분 유니크 인덱스로 보장) — 재등록 시 기존 ACTIVE 를 조회해 무효화한다.
    fun findFirstByCustomerIdAndStatus(customerId: Long, status: BillingKeyStatus): BillingKeyJpaEntity?

    fun existsByCustomerIdAndStatus(customerId: Long, status: BillingKeyStatus): Boolean
}
