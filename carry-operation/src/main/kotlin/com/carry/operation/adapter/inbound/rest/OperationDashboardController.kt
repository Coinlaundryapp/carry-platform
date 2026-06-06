package com.carry.operation.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.operation.adapter.inbound.rest.dto.OperationEventResponse
import com.carry.operation.adapter.inbound.rest.dto.OperationSummaryResponse
import com.carry.operation.application.port.inbound.OperationQueryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Dashboard", description = "운영 대시보드 API")
@RestController
@RequestMapping("/api/v2/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
class OperationDashboardController(
    private val operationQueryUseCase: OperationQueryUseCase,
) {

    @Operation(summary = "운영 요약 조회", description = "오늘의 주문, 배차, 배달 현황을 요약합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "요약 조회 성공")])
    @GetMapping("/summary")
    fun getSummary(): ResponseEntity<ApiResponse<OperationSummaryResponse>> {
        val summary = operationQueryUseCase.getSummary()
        return ResponseEntity.ok(ApiResponse.success(OperationSummaryResponse.from(summary)))
    }

    @Operation(summary = "최근 이벤트 목록 조회", description = "시스템 운영 이벤트를 최신순으로 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "이벤트 목록 조회 성공")])
    @GetMapping("/events")
    fun getRecentEvents(
        @Parameter(description = "조회할 이벤트 수", example = "50") @RequestParam(defaultValue = "50") limit: Int,
    ): ResponseEntity<ApiResponse<List<OperationEventResponse>>> {
        val events = operationQueryUseCase.getRecentEvents(limit)
        return ResponseEntity.ok(ApiResponse.success(events.map { OperationEventResponse.from(it) }))
    }
}
