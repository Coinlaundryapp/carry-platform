package com.carry.operation.application.service

import com.carry.operation.application.port.inbound.TermQueryUseCase
import com.carry.operation.application.port.outbound.TermPersistencePort
import com.carry.operation.domain.exception.TermNotFoundException
import com.carry.operation.domain.model.Term
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TermQueryService(
    private val termPersistencePort: TermPersistencePort,
) : TermQueryUseCase {

    override fun getTerm(termId: Long): Term {
        return termPersistencePort.findById(termId)
            ?: throw TermNotFoundException(termId)
    }

    override fun getActiveTerms(): List<Term> {
        return termPersistencePort.findByActive(true)
    }

    override fun getRequiredTerms(): List<Term> {
        return termPersistencePort.findByRequiredAndActive(required = true, active = true)
    }
}
