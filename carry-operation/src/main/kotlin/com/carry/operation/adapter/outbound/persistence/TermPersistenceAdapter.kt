package com.carry.operation.adapter.outbound.persistence

import com.carry.operation.adapter.outbound.persistence.entity.TermJpaEntity
import com.carry.operation.adapter.outbound.persistence.repository.TermJpaRepository
import com.carry.operation.application.port.outbound.TermPersistencePort
import com.carry.operation.domain.model.Term
import org.springframework.stereotype.Component

@Component
class TermPersistenceAdapter(
    private val termJpaRepository: TermJpaRepository,
) : TermPersistencePort {

    override fun save(term: Term): Term {
        val entity = if (term.id == null) {
            TermJpaEntity.fromDomain(term)
        } else {
            val existing = termJpaRepository.getReferenceById(term.id)
            existing.updateFrom(term)
            existing
        }
        return termJpaRepository.save(entity).toDomain()
    }

    override fun findById(termId: Long): Term? {
        return termJpaRepository.findById(termId).orElse(null)?.toDomain()
    }

    override fun findByActive(active: Boolean): List<Term> {
        return termJpaRepository.findByActive(active).map { it.toDomain() }
    }

    override fun findByRequiredAndActive(required: Boolean, active: Boolean): List<Term> {
        return termJpaRepository.findByRequiredAndActive(required, active).map { it.toDomain() }
    }
}
