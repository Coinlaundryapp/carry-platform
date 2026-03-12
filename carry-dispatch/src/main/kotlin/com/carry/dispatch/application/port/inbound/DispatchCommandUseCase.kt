package com.carry.dispatch.application.port.inbound

import com.carry.dispatch.domain.model.Dispatch

data class ClaimDispatchCommand(
    val dispatchId: Long,
    val carrierId: Long,
)

data class AssignDispatchCommand(
    val dispatchId: Long,
    val carrierId: Long,
)

data class AcceptAssignmentCommand(
    val dispatchId: Long,
    val carrierId: Long,
)

data class RejectAssignmentCommand(
    val dispatchId: Long,
    val carrierId: Long,
)

data class CancelDispatchCommand(
    val dispatchId: Long,
    val reason: String,
)

interface DispatchCommandUseCase {
    fun claimDispatch(command: ClaimDispatchCommand): Dispatch
    fun assignDispatch(command: AssignDispatchCommand): Dispatch
    fun acceptAssignment(command: AcceptAssignmentCommand): Dispatch
    fun rejectAssignment(command: RejectAssignmentCommand): Dispatch
    fun cancelDispatch(command: CancelDispatchCommand)
}
