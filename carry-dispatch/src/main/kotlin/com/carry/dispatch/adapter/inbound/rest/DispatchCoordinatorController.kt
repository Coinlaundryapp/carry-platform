package com.carry.dispatch.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.dispatch.adapter.inbound.rest.dto.AssignDispatchRequest
import com.carry.dispatch.adapter.inbound.rest.dto.CancelDispatchRequest
import com.carry.dispatch.adapter.inbound.rest.dto.CarrierAreaResponse
import com.carry.dispatch.adapter.inbound.rest.dto.DispatchResponse
import com.carry.dispatch.application.port.inbound.AssignDispatchCommand
import com.carry.dispatch.application.port.inbound.CancelDispatchCommand
import com.carry.dispatch.application.port.inbound.CarrierAreaUseCase
import com.carry.dispatch.application.port.inbound.DispatchCommandUseCase
import com.carry.dispatch.application.port.inbound.DispatchQueryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Dispatch - Admin", description = "배차 관리자 API")
@RestController
@RequestMapping("/api/v2/admin/dispatches")
class DispatchCoordinatorController(
    private val dispatchCommandUseCase: DispatchCommandUseCase,
    private val dispatchQueryUseCase: DispatchQueryUseCase,
    private val carrierAreaUseCase: CarrierAreaUseCase,
) {

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

    @Operation(summary = "배차 배정", description = "특정 배달원에게 배차를 배정합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "배차 배정 성공")])
    @PostMapping("/{dispatchId}/assign")
    fun assignDispatch(
        @PathVariable dispatchId: Long,
        @Valid @RequestBody request: AssignDispatchRequest,
    ): ResponseEntity<ApiResponse<DispatchResponse>> {
        val dispatch = dispatchCommandUseCase.assignDispatch(
            AssignDispatchCommand(dispatchId, request.carrierId),
        )
        return ResponseEntity.ok(ApiResponse.success(DispatchResponse.from(dispatch)))
    }

    @Operation(summary = "배차 취소")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "204", description = "배차 취소 성공")])
    @PostMapping("/{dispatchId}/cancel")
    fun cancelDispatch(
        @PathVariable dispatchId: Long,
        @Valid @RequestBody request: CancelDispatchRequest,
    ): ResponseEntity<Void> {
        dispatchCommandUseCase.cancelDispatch(CancelDispatchCommand(dispatchId, request.reason))
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "권역별 배달원 조회", description = "특정 권역의 배달원 목록을 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "배달원 목록 조회 성공")])
    @GetMapping("/carriers")
    fun getCarriersByArea(
        @RequestParam areaCode: String,
    ): ResponseEntity<ApiResponse<List<CarrierAreaResponse>>> {
        val carriers = carrierAreaUseCase.getCarriersByArea(areaCode)
        return ResponseEntity.ok(ApiResponse.success(carriers.map { CarrierAreaResponse.from(it) }))
    }
}
