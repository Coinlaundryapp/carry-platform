package com.carry.dispatch.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.dispatch.adapter.inbound.rest.dto.AcceptAssignmentRequest
import com.carry.dispatch.adapter.inbound.rest.dto.ClaimDispatchRequest
import com.carry.dispatch.adapter.inbound.rest.dto.DispatchResponse
import com.carry.dispatch.adapter.inbound.rest.dto.RejectAssignmentRequest
import com.carry.dispatch.application.port.inbound.AcceptAssignmentCommand
import com.carry.dispatch.application.port.inbound.ClaimDispatchCommand
import com.carry.dispatch.application.port.inbound.DispatchCommandUseCase
import com.carry.dispatch.application.port.inbound.DispatchQueryUseCase
import com.carry.dispatch.application.port.inbound.RejectAssignmentCommand
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/dispatches")
class DispatchCarrierController(
    private val dispatchCommandUseCase: DispatchCommandUseCase,
    private val dispatchQueryUseCase: DispatchQueryUseCase,
) {

    @GetMapping("/available")
    fun getAvailableDispatches(
        @RequestParam carrierId: Long,
    ): ResponseEntity<ApiResponse<List<DispatchResponse>>> {
        val dispatches = dispatchQueryUseCase.getAvailableDispatches(carrierId)
        return ResponseEntity.ok(ApiResponse.success(dispatches.map { DispatchResponse.from(it) }))
    }

    @PostMapping("/{dispatchId}/claim")
    fun claimDispatch(
        @PathVariable dispatchId: Long,
        @Valid @RequestBody request: ClaimDispatchRequest,
    ): ResponseEntity<ApiResponse<DispatchResponse>> {
        val dispatch = dispatchCommandUseCase.claimDispatch(
            ClaimDispatchCommand(dispatchId, request.carrierId),
        )
        return ResponseEntity.ok(ApiResponse.success(DispatchResponse.from(dispatch)))
    }

    @PostMapping("/{dispatchId}/accept")
    fun acceptAssignment(
        @PathVariable dispatchId: Long,
        @Valid @RequestBody request: AcceptAssignmentRequest,
    ): ResponseEntity<ApiResponse<DispatchResponse>> {
        val dispatch = dispatchCommandUseCase.acceptAssignment(
            AcceptAssignmentCommand(dispatchId, request.carrierId),
        )
        return ResponseEntity.ok(ApiResponse.success(DispatchResponse.from(dispatch)))
    }

    @PostMapping("/{dispatchId}/reject")
    fun rejectAssignment(
        @PathVariable dispatchId: Long,
        @Valid @RequestBody request: RejectAssignmentRequest,
    ): ResponseEntity<ApiResponse<DispatchResponse>> {
        val dispatch = dispatchCommandUseCase.rejectAssignment(
            RejectAssignmentCommand(dispatchId, request.carrierId),
        )
        return ResponseEntity.ok(ApiResponse.success(DispatchResponse.from(dispatch)))
    }

    @GetMapping("/my")
    fun getMyDispatches(
        @RequestParam carrierId: Long,
    ): ResponseEntity<ApiResponse<List<DispatchResponse>>> {
        val dispatches = dispatchQueryUseCase.getDispatchesByCarrier(carrierId)
        return ResponseEntity.ok(ApiResponse.success(dispatches.map { DispatchResponse.from(it) }))
    }

    @GetMapping("/{dispatchId}")
    fun getDispatch(
        @PathVariable dispatchId: Long,
    ): ResponseEntity<ApiResponse<DispatchResponse>> {
        val dispatch = dispatchQueryUseCase.getDispatch(dispatchId)
        return ResponseEntity.ok(ApiResponse.success(DispatchResponse.from(dispatch)))
    }
}
