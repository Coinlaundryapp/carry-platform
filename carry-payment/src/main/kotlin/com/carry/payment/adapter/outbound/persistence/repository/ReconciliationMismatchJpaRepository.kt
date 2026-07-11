package com.carry.payment.adapter.outbound.persistence.repository

import com.carry.payment.adapter.outbound.persistence.entity.ReconciliationMismatchJpaEntity
import com.carry.payment.domain.vo.ReconciliationMismatchType
import org.springframework.data.jpa.repository.JpaRepository

interface ReconciliationMismatchJpaRepository : JpaRepository<ReconciliationMismatchJpaEntity, Long> {
    fun existsByMismatchTypeAndDedupKey(mismatchType: ReconciliationMismatchType, dedupKey: String): Boolean
}
