package com.carry.dispatch.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.dispatch.adapter.inbound.rest.dto.DispatchResponse
import com.carry.dispatch.application.port.inbound.AcceptAssignmentCommand
import com.carry.dispatch.application.port.inbound.ClaimDispatchCommand
import com.carry.dispatch.application.port.inbound.DispatchCommandUseCase
import com.carry.dispatch.application.port.inbound.DispatchQueryUseCase
import com.carry.dispatch.application.port.inbound.RejectAssignmentCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal

@Tag(name = "Dispatch - Carrier", description = "배달원 배차 API")
@RestController
@RequestMapping("/api/v2/dispatches")
@PreAuthorize("hasRole('CARRIER')")
class DispatchCarrierController(
    private val dispatchCommandUseCase: DispatchCommandUseCase,
    private val dispatchQueryUseCase: DispatchQueryUseCase,
) {

    @Operation(summary = "수락 가능한 배차 목록 조회")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "배차 목록 조회 성공")])
    @GetMapping("/available")
    fun getAvailableDispatches(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "커서 (마지막 배차 ID)") @RequestParam(required = false) cursor: Long?,
        @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<List<DispatchResponse>>> {
        val dispatches = dispatchQueryUseCase.getAvailableDispatches(userId, cursor, size)
        return ResponseEntity.ok(ApiResponse.success(dispatches.map { DispatchResponse.from(it) }))
    }

    @Operation(summary = "배차 선점", description = "공개된 배차를 배달원이 선점합니다")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "배차 선점 성공"),
            SwaggerApiResponse(responseCode = "409", description = "이미 선점된 배차"),
        ],
    )
    @PostMapping("/{dispatchId}/claim")
    fun claimDispatch(
        @PathVariable dispatchId: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<DispatchResponse>> {
        val dispatch = dispatchCommandUseCase.claimDispatch(
            ClaimDispatchCommand(dispatchId, userId),
        )
        return ResponseEntity.ok(ApiResponse.success(DispatchResponse.from(dispatch)))
    }

    @Operation(summary = "배차 수락", description = "배정된 배차를 수락합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "배차 수락 성공")])
    @PostMapping("/{dispatchId}/accept")
    fun acceptAssignment(
        @PathVariable dispatchId: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<DispatchResponse>> {
        val dispatch = dispatchCommandUseCase.acceptAssignment(
            AcceptAssignmentCommand(dispatchId, userId),
        )
        return ResponseEntity.ok(ApiResponse.success(DispatchResponse.from(dispatch)))
    }

    @Operation(summary = "배차 거절", description = "배정된 배차를 거절합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "배차 거절 성공")])
    @PostMapping("/{dispatchId}/reject")
    fun rejectAssignment(
        @PathVariable dispatchId: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<DispatchResponse>> {
        val dispatch = dispatchCommandUseCase.rejectAssignment(
            RejectAssignmentCommand(dispatchId, userId),
        )
        return ResponseEntity.ok(ApiResponse.success(DispatchResponse.from(dispatch)))
    }

    @Operation(summary = "내 배차 목록 조회")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "배차 목록 조회 성공")])
    @GetMapping("/my")
    fun getMyDispatches(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "커서 (마지막 배차 ID)") @RequestParam(required = false) cursor: Long?,
        @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<List<DispatchResponse>>> {
        val dispatches = dispatchQueryUseCase.getDispatchesByCarrier(userId, cursor, size)
        return ResponseEntity.ok(ApiResponse.success(dispatches.map { DispatchResponse.from(it) }))
    }

    @Operation(summary = "배차 상세 조회")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "배차 조회 성공"),
            SwaggerApiResponse(responseCode = "404", description = "배차를 찾을 수 없음"),
        ],
    )
    @GetMapping("/{dispatchId}")
    fun getDispatch(
        @PathVariable dispatchId: Long,
    ): ResponseEntity<ApiResponse<DispatchResponse>> {
        val dispatch = dispatchQueryUseCase.getDispatch(dispatchId)
        return ResponseEntity.ok(ApiResponse.success(DispatchResponse.from(dispatch)))
    }
}
