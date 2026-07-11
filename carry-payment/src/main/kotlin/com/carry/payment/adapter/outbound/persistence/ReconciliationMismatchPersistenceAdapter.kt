package com.carry.payment.adapter.outbound.persistence

import com.carry.payment.adapter.outbound.persistence.entity.ReconciliationMismatchJpaEntity
import com.carry.payment.adapter.outbound.persistence.repository.ReconciliationMismatchJpaRepository
import com.carry.payment.application.port.outbound.ReconciliationMismatchPort
import com.carry.payment.domain.model.ReconciliationMismatch
import org.springframework.stereotype.Component

@Component
class ReconciliationMismatchPersistenceAdapter(
    private val repository: ReconciliationMismatchJpaRepository,
) : ReconciliationMismatchPort {

    override fun recordIfNew(mismatch: ReconciliationMismatch): Boolean {
        // 대사 잡은 @SchedulerLock 단일 실행자라 exists→insert 로 충분하며,
        // (mismatch_type, dedup_key) UNIQUE 인덱스가 최후 안전망.
        if (repository.existsByMismatchTypeAndDedupKey(mismatch.type, mismatch.dedupKey)) {
            return false
        }
        repository.save(ReconciliationMismatchJpaEntity.fromDomain(mismatch))
        return true
    }
}
