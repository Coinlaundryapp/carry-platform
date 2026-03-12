package com.carry.operation.application.port.inbound

import com.carry.operation.domain.model.Term

interface TermQueryUseCase {
    fun getTerm(termId: Long): Term
    fun getActiveTerms(): List<Term>
    fun getRequiredTerms(): List<Term>
}
