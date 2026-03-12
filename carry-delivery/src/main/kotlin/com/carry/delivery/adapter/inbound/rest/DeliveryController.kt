package com.carry.delivery.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.delivery.adapter.inbound.rest.dto.CompletePickupRequest
import com.carry.delivery.adapter.inbound.rest.dto.DeliveryResponse
import com.carry.delivery.adapter.inbound.rest.dto.StepPhotoRequest
import com.carry.delivery.application.port.inbound.DeliveryCommandUseCase
import com.carry.delivery.application.port.inbound.DeliveryQueryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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

@Tag(name = "Delivery", description = "배달 프로세스 관리 API")
@RestController
@RequestMapping("/api/v2/deliveries")
class DeliveryController(
    private val deliveryCommandUseCase: DeliveryCommandUseCase,
    private val deliveryQueryUseCase: DeliveryQueryUseCase,
) {

    @Operation(summary = "배달원 배달 목록 조회")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "배달 목록 조회 성공")])
    @GetMapping("/my")
    fun getMyDeliveries(
        @RequestParam carrierId: Long,
        @Parameter(description = "마지막으로 조회한 배달 ID (첫 페이지는 생략)") @RequestParam(required = false) cursor: Long?,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<List<DeliveryResponse>>> {
        val deliveries = deliveryQueryUseCase.getDeliveriesByCarrier(carrierId, cursor, size)
        return ResponseEntity.ok(ApiResponse.success(deliveries.map { DeliveryResponse.from(it) }))
    }

    @Operation(summary = "배달 상세 조회")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "배달 조회 성공"), SwaggerApiResponse(responseCode = "404", description = "배달을 찾을 수 없음")])
    @GetMapping("/{deliveryId}")
    fun getDelivery(@PathVariable deliveryId: Long): ResponseEntity<ApiResponse<DeliveryResponse>> {
        val delivery = deliveryQueryUseCase.getDelivery(deliveryId)
        return ResponseEntity.ok(ApiResponse.success(DeliveryResponse.from(delivery)))
    }

    @Operation(summary = "수거 완료", description = "세탁물 수거를 완료하고 무게를 기록합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "수거 완료 처리 성공"), SwaggerApiResponse(responseCode = "400", description = "잘못된 요청")])
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

    @Operation(summary = "세탁 시작", description = "세탁기 투입 사진과 함께 세탁 시작을 기록합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "세탁 시작 처리 성공")])
    @PostMapping("/{deliveryId}/washing")
    fun startWashing(
        @PathVariable deliveryId: Long,
        @Valid @RequestBody request: StepPhotoRequest,
    ): ResponseEntity<ApiResponse<DeliveryResponse>> {
        val delivery = deliveryCommandUseCase.startWashing(deliveryId, request.photoIds)
        return ResponseEntity.ok(ApiResponse.success(DeliveryResponse.from(delivery)))
    }

    @Operation(summary = "건조 완료", description = "건조 완료 사진과 함께 건조 완료를 기록합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "건조 완료 처리 성공")])
    @PostMapping("/{deliveryId}/drying")
    fun completeDrying(
        @PathVariable deliveryId: Long,
        @Valid @RequestBody request: StepPhotoRequest,
    ): ResponseEntity<ApiResponse<DeliveryResponse>> {
        val delivery = deliveryCommandUseCase.completeDrying(deliveryId, request.photoIds)
        return ResponseEntity.ok(ApiResponse.success(DeliveryResponse.from(delivery)))
    }

    @Operation(summary = "배달 완료", description = "배달 완료 사진과 함께 배달 완료를 기록합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "배달 완료 처리 성공")])
    @PostMapping("/{deliveryId}/delivery")
    fun completeDelivery(
        @PathVariable deliveryId: Long,
        @Valid @RequestBody request: StepPhotoRequest,
    ): ResponseEntity<ApiResponse<DeliveryResponse>> {
        val delivery = deliveryCommandUseCase.completeDelivery(deliveryId, request.photoIds)
        return ResponseEntity.ok(ApiResponse.success(DeliveryResponse.from(delivery)))
    }
}
