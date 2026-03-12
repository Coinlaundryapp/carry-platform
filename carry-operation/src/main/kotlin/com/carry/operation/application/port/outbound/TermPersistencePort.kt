package com.carry.operation.application.port.outbound

import com.carry.operation.domain.model.Term

interface TermPersistencePort {
    fun save(term: Term): Term
    fun findById(termId: Long): Term?
    fun findByActive(active: Boolean): List<Term>
    fun findByRequiredAndActive(required: Boolean, active: Boolean): List<Term>
}
