package com.carry.operation.application.port.inbound

import com.carry.operation.domain.model.Term
import com.carry.operation.domain.vo.TermType

data class CreateTermCommand(
    val title: String,
    val content: String,
    val type: TermType,
    val required: Boolean,
)

data class UpdateTermCommand(
    val termId: Long,
    val title: String,
    val content: String,
    val required: Boolean,
)

interface TermCommandUseCase {
    fun createTerm(command: CreateTermCommand): Term
    fun updateTerm(command: UpdateTermCommand): Term
    fun deactivateTerm(termId: Long)
}
