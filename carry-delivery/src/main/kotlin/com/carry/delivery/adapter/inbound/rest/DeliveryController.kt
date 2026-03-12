package com.carry.delivery.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.delivery.adapter.inbound.rest.dto.CompletePickupRequest
import com.carry.delivery.adapter.inbound.rest.dto.DeliveryResponse
import com.carry.delivery.adapter.inbound.rest.dto.StepPhotoRequest
import com.carry.delivery.application.port.inbound.DeliveryCommandUseCase
import com.carry.delivery.application.port.inbound.DeliveryQueryUseCase
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
@RequestMapping("/api/v2/deliveries")
class DeliveryController(
    private val deliveryCommandUseCase: DeliveryCommandUseCase,
    private val deliveryQueryUseCase: DeliveryQueryUseCase,
) {

    @GetMapping("/my")
    fun getMyDeliveries(@RequestParam carrierId: Long): ResponseEntity<ApiResponse<List<DeliveryResponse>>> {
        val deliveries = deliveryQueryUseCase.getDeliveriesByCarrier(carrierId)
        return ResponseEntity.ok(ApiResponse.success(deliveries.map { DeliveryResponse.from(it) }))
    }

    @GetMapping("/{deliveryId}")
    fun getDelivery(@PathVariable deliveryId: Long): ResponseEntity<ApiResponse<DeliveryResponse>> {
        val delivery = deliveryQueryUseCase.getDelivery(deliveryId)
        return ResponseEntity.ok(ApiResponse.success(DeliveryResponse.from(delivery)))
    }

    @PostMapping("/{deliveryId}/pickup")
    fun completePickup(
        @PathVariable deliveryId: Long,
        @Valid @RequestBody request: CompletePickupRequest,
    ): ResponseEntity<ApiResponse<DeliveryResponse>> {
        val delivery = deliveryCommandUseCase.completePickup(
            deliveryId = deliveryId,
            weight = request.weight,
            photoIds = request.photoIds,
            customerId = request.customerId,
            laundryItemType = request.laundryItemType,
            orderUnitType = request.orderUnitType,
            orderRequestType = request.orderRequestType,
            selectedOptions = request.selectedOptions,
        )
        return ResponseEntity.ok(ApiResponse.success(DeliveryResponse.from(delivery)))
    }

    @PostMapping("/{deliveryId}/washing")
    fun startWashing(
        @PathVariable deliveryId: Long,
        @Valid @RequestBody request: StepPhotoRequest,
    ): ResponseEntity<ApiResponse<DeliveryResponse>> {
        val delivery = deliveryCommandUseCase.startWashing(deliveryId, request.photoIds)
        return ResponseEntity.ok(ApiResponse.success(DeliveryResponse.from(delivery)))
    }

    @PostMapping("/{deliveryId}/drying")
    fun completeDrying(
        @PathVariable deliveryId: Long,
        @Valid @RequestBody request: StepPhotoRequest,
    ): ResponseEntity<ApiResponse<DeliveryResponse>> {
        val delivery = deliveryCommandUseCase.completeDrying(deliveryId, request.photoIds)
        return ResponseEntity.ok(ApiResponse.success(DeliveryResponse.from(delivery)))
    }

    @PostMapping("/{deliveryId}/delivery")
    fun completeDelivery(
        @PathVariable deliveryId: Long,
        @Valid @RequestBody request: StepPhotoRequest,
    ): ResponseEntity<ApiResponse<DeliveryResponse>> {
        val delivery = deliveryCommandUseCase.completeDelivery(deliveryId, request.photoIds)
        return ResponseEntity.ok(ApiResponse.success(DeliveryResponse.from(delivery)))
    }
}
