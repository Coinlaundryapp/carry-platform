package com.carry.dispatch.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.dispatch.adapter.inbound.rest.dto.CarrierAreaResponse
import com.carry.dispatch.adapter.inbound.rest.dto.RegisterAreaRequest
import com.carry.dispatch.application.port.inbound.CarrierAreaUseCase
import com.carry.dispatch.application.port.inbound.RegisterAreaCommand
import com.carry.dispatch.application.port.inbound.RemoveAreaCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Carrier Area", description = "배달원 권역 관리 API")
@RestController
@RequestMapping("/api/v2/carrier-areas")
class CarrierAreaController(
    private val carrierAreaUseCase: CarrierAreaUseCase,
) {

    @Operation(summary = "배달원 권역 등록")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "권역 등록 성공"),
            SwaggerApiResponse(responseCode = "409", description = "이미 등록된 권역"),
        ],
    )
    @PostMapping
    fun registerArea(
        @RequestParam carrierId: Long,
        @Valid @RequestBody request: RegisterAreaRequest,
    ): ResponseEntity<ApiResponse<CarrierAreaResponse>> {
        val carrierArea = carrierAreaUseCase.registerArea(
            RegisterAreaCommand(carrierId, request.areaCode, request.areaName),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(CarrierAreaResponse.from(carrierArea)))
    }

    @Operation(summary = "배달원 권역 해제")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "204", description = "권역 해제 성공")])
    @DeleteMapping
    fun removeArea(
        @RequestParam carrierId: Long,
        @RequestParam areaCode: String,
    ): ResponseEntity<Void> {
        carrierAreaUseCase.removeArea(RemoveAreaCommand(carrierId, areaCode))
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "배달원 권역 목록 조회")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "권역 목록 조회 성공")])
    @GetMapping
    fun getAreasByCarrier(
        @RequestParam carrierId: Long,
    ): ResponseEntity<ApiResponse<List<CarrierAreaResponse>>> {
        val areas = carrierAreaUseCase.getAreasByCarrier(carrierId)
        return ResponseEntity.ok(ApiResponse.success(areas.map { CarrierAreaResponse.from(it) }))
    }
}
