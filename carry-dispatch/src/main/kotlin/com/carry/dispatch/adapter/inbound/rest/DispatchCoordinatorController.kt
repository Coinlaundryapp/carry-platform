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
@RequestMapping("/api/v2/admin/dispatches")
class DispatchCoordinatorController(
    private val dispatchCommandUseCase: DispatchCommandUseCase,
    private val dispatchQueryUseCase: DispatchQueryUseCase,
    private val carrierAreaUseCase: CarrierAreaUseCase,
) {

    @GetMapping("/{dispatchId}")
    fun getDispatch(
        @PathVariable dispatchId: Long,
    ): ResponseEntity<ApiResponse<DispatchResponse>> {
        val dispatch = dispatchQueryUseCase.getDispatch(dispatchId)
        return ResponseEntity.ok(ApiResponse.success(DispatchResponse.from(dispatch)))
    }

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

    @PostMapping("/{dispatchId}/cancel")
    fun cancelDispatch(
        @PathVariable dispatchId: Long,
        @Valid @RequestBody request: CancelDispatchRequest,
    ): ResponseEntity<Void> {
        dispatchCommandUseCase.cancelDispatch(CancelDispatchCommand(dispatchId, request.reason))
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/carriers")
    fun getCarriersByArea(
        @RequestParam areaCode: String,
    ): ResponseEntity<ApiResponse<List<CarrierAreaResponse>>> {
        val carriers = carrierAreaUseCase.getCarriersByArea(areaCode)
        return ResponseEntity.ok(ApiResponse.success(carriers.map { CarrierAreaResponse.from(it) }))
    }
}
