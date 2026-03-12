package com.carry.dispatch.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.dispatch.adapter.inbound.rest.dto.CarrierAreaResponse
import com.carry.dispatch.adapter.inbound.rest.dto.RegisterAreaRequest
import com.carry.dispatch.application.port.inbound.CarrierAreaUseCase
import com.carry.dispatch.application.port.inbound.RegisterAreaCommand
import com.carry.dispatch.application.port.inbound.RemoveAreaCommand
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

@RestController
@RequestMapping("/api/v2/carrier-areas")
class CarrierAreaController(
    private val carrierAreaUseCase: CarrierAreaUseCase,
) {

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

    @DeleteMapping
    fun removeArea(
        @RequestParam carrierId: Long,
        @RequestParam areaCode: String,
    ): ResponseEntity<Void> {
        carrierAreaUseCase.removeArea(RemoveAreaCommand(carrierId, areaCode))
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    fun getAreasByCarrier(
        @RequestParam carrierId: Long,
    ): ResponseEntity<ApiResponse<List<CarrierAreaResponse>>> {
        val areas = carrierAreaUseCase.getAreasByCarrier(carrierId)
        return ResponseEntity.ok(ApiResponse.success(areas.map { CarrierAreaResponse.from(it) }))
    }
}
