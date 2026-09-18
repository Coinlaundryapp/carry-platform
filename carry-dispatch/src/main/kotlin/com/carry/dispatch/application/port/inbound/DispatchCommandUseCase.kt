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

    /**
     * 수거 시한이 임박하도록 PENDING 으로 남은 배차를 TIMEOUT 으로 종결한다.
     * 시스템(스위퍼)이 트리거하며, 이미 다른 인스턴스가 종결했으면 도메인 가드가 예외를 던져 멱등하다.
     */
    fun timeoutDispatch(dispatchId: Long): Dispatch
}
