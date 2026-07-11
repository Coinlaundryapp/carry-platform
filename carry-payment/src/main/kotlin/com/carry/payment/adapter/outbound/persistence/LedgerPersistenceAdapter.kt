package com.carry.payment.adapter.outbound.persistence

import com.carry.payment.adapter.outbound.persistence.entity.LedgerEntryJpaEntity
import com.carry.payment.adapter.outbound.persistence.repository.LedgerEntryJpaRepository
import com.carry.payment.application.port.outbound.LedgerPort
import com.carry.payment.domain.model.LedgerEntry
import com.carry.payment.domain.vo.LedgerAccountType
import org.springframework.stereotype.Component

@Component
class LedgerPersistenceAdapter(
    private val repository: LedgerEntryJpaRepository,
) : LedgerPort {

    override fun record(entries: List<LedgerEntry>) {
        repository.saveAll(entries.map { LedgerEntryJpaEntity.fromDomain(it) })
    }

    override fun balance(accountType: LedgerAccountType, accountId: Long?): Long =
        if (accountId != null) {
            repository.sumByAccount(accountType, accountId)
        } else {
            repository.sumByAccountTypeWithoutId(accountType)
        }
}
