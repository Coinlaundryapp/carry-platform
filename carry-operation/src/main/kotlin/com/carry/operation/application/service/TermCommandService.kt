package com.carry.operation.application.service

import com.carry.operation.application.port.inbound.CreateTermCommand
import com.carry.operation.application.port.inbound.TermCommandUseCase
import com.carry.operation.application.port.inbound.UpdateTermCommand
import com.carry.operation.application.port.outbound.TermPersistencePort
import com.carry.operation.domain.exception.TermNotFoundException
import com.carry.operation.domain.model.Term
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TermCommandService(
    private val termPersistencePort: TermPersistencePort,
) : TermCommandUseCase {

    @Transactional
    override fun createTerm(command: CreateTermCommand): Term {
        val term = Term.create(
            title = command.title,
            content = command.content,
            type = command.type,
            required = command.required,
        )
        return termPersistencePort.save(term)
    }

    @Transactional
    override fun updateTerm(command: UpdateTermCommand): Term {
        val term = termPersistencePort.findById(command.termId)
            ?: throw TermNotFoundException(command.termId)
        term.update(
            title = command.title,
            content = command.content,
            required = command.required,
        )
        return termPersistencePort.save(term)
    }

    @Transactional
    override fun deactivateTerm(termId: Long) {
        val term = termPersistencePort.findById(termId)
            ?: throw TermNotFoundException(termId)
        term.deactivate()
        termPersistencePort.save(term)
    }
}
