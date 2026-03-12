package com.carry.laundromat.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.laundromat.adapter.inbound.rest.dto.AddMediaResourceRequest
import com.carry.laundromat.adapter.inbound.rest.dto.LaundromatResponse
import com.carry.laundromat.adapter.inbound.rest.dto.NearbyLaundromatResponse
import com.carry.laundromat.adapter.inbound.rest.dto.RegisterLaundromatRequest
import com.carry.laundromat.adapter.inbound.rest.dto.UpdateLaundromatInfoRequest
import com.carry.laundromat.adapter.inbound.rest.dto.UpdateOptionsRequest
import com.carry.laundromat.application.port.inbound.LaundromatCommandUseCase
import com.carry.laundromat.application.port.inbound.LaundromatQueryUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/laundromats")
class LaundromatController(
    private val laundromatQueryUseCase: LaundromatQueryUseCase,
    private val laundromatCommandUseCase: LaundromatCommandUseCase,
) {

    @GetMapping
    fun findNearby(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(defaultValue = "3000") radiusMeters: Int,
    ): ResponseEntity<ApiResponse<List<NearbyLaundromatResponse>>> {
        val result = laundromatQueryUseCase.findNearby(latitude, longitude, radiusMeters)
            .map { NearbyLaundromatResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<LaundromatResponse>> {
        val laundromat = laundromatQueryUseCase.getById(id)
        return ResponseEntity.ok(ApiResponse.success(LaundromatResponse.from(laundromat)))
    }

    @PostMapping
    fun register(
        @RequestBody request: RegisterLaundromatRequest,
    ): ResponseEntity<ApiResponse<LaundromatResponse>> {
        val laundromat = laundromatCommandUseCase.register(
            name = request.name,
            address = request.toAddress(),
            location = request.toLocation(),
            options = request.options,
        )
        return ResponseEntity.status(201).body(ApiResponse.created(LaundromatResponse.from(laundromat)))
    }

    @PutMapping("/{id}")
    fun updateInfo(
        @PathVariable id: Long,
        @RequestBody request: UpdateLaundromatInfoRequest,
    ): ResponseEntity<ApiResponse<LaundromatResponse>> {
        val laundromat = laundromatCommandUseCase.updateInfo(
            laundromatId = id,
            name = request.name,
            address = request.toAddress(),
            location = request.toLocation(),
        )
        return ResponseEntity.ok(ApiResponse.success(LaundromatResponse.from(laundromat)))
    }

    @PutMapping("/{id}/options")
    fun updateOptions(
        @PathVariable id: Long,
        @RequestBody request: UpdateOptionsRequest,
    ): ResponseEntity<ApiResponse<LaundromatResponse>> {
        val laundromat = laundromatCommandUseCase.updateOptions(id, request.options)
        return ResponseEntity.ok(ApiResponse.success(LaundromatResponse.from(laundromat)))
    }

    @PostMapping("/{id}/media")
    fun addMediaResource(
        @PathVariable id: Long,
        @RequestBody request: AddMediaResourceRequest,
    ): ResponseEntity<ApiResponse<LaundromatResponse>> {
        val laundromat = laundromatCommandUseCase.addMediaResource(id, request.url, request.extension)
        return ResponseEntity.status(201).body(ApiResponse.created(LaundromatResponse.from(laundromat)))
    }

    @DeleteMapping("/{id}/media/{mediaResourceId}")
    fun removeMediaResource(
        @PathVariable id: Long,
        @PathVariable mediaResourceId: Long,
    ): ResponseEntity<ApiResponse<LaundromatResponse>> {
        val laundromat = laundromatCommandUseCase.removeMediaResource(id, mediaResourceId)
        return ResponseEntity.ok(ApiResponse.success(LaundromatResponse.from(laundromat)))
    }
}
