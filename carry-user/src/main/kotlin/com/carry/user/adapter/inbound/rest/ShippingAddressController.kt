package com.carry.user.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.user.adapter.inbound.rest.dto.CreateShippingAddressRequest
import com.carry.user.adapter.inbound.rest.dto.ShippingAddressResponse
import com.carry.user.adapter.inbound.rest.dto.UpdateShippingAddressRequest
import com.carry.user.application.port.inbound.ShippingAddressUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Shipping Address", description = "배송지 관리 API")
@RestController
@RequestMapping("/api/v2/shipping-addresses")
class ShippingAddressController(
    private val shippingAddressUseCase: ShippingAddressUseCase,
) {

    @Operation(summary = "내 배송지 목록 조회")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "배송지 목록 조회 성공")])
    @GetMapping
    fun getMyAddresses(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<List<ShippingAddressResponse>>> {
        val addresses = shippingAddressUseCase.getAddresses(userId)
            .map { ShippingAddressResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.success(addresses))
    }

    @Operation(summary = "배송지 등록", description = "새 배송지를 등록합니다 (최대 5개)")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "201", description = "배송지 등록 성공"), SwaggerApiResponse(responseCode = "400", description = "잘못된 요청"), SwaggerApiResponse(responseCode = "409", description = "배송지 개수 초과")])
    @PostMapping
    fun createAddress(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: CreateShippingAddressRequest,
    ): ResponseEntity<ApiResponse<ShippingAddressResponse>> {
        val address = shippingAddressUseCase.createAddress(
            userId = userId,
            alias = request.alias,
            address = request.toAddress(),
            coordinates = request.toCoordinates(),
            recipientName = request.recipientName,
            recipientPhone = request.recipientPhone,
            entranceInfo = request.entranceInfo,
            areaCode = request.areaCode,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(ShippingAddressResponse.from(address)))
    }

    @Operation(summary = "배송지 수정")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "배송지 수정 성공"), SwaggerApiResponse(responseCode = "404", description = "배송지를 찾을 수 없음")])
    @PutMapping("/{addressId}")
    fun updateAddress(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable addressId: Long,
        @Valid @RequestBody request: UpdateShippingAddressRequest,
    ): ResponseEntity<ApiResponse<ShippingAddressResponse>> {
        val address = shippingAddressUseCase.updateAddress(
            userId = userId,
            addressId = addressId,
            alias = request.alias,
            address = request.toAddress(),
            coordinates = request.toCoordinates(),
            recipientName = request.recipientName,
            recipientPhone = request.recipientPhone,
            entranceInfo = request.entranceInfo,
            areaCode = request.areaCode,
        )
        return ResponseEntity.ok(ApiResponse.success(ShippingAddressResponse.from(address)))
    }

    @Operation(summary = "배송지 삭제")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "204", description = "배송지 삭제 성공")])
    @DeleteMapping("/{addressId}")
    fun deleteAddress(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable addressId: Long,
    ): ResponseEntity<Void> {
        shippingAddressUseCase.deleteAddress(userId, addressId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "기본 배송지 설정")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "기본 배송지 설정 성공"), SwaggerApiResponse(responseCode = "404", description = "배송지를 찾을 수 없음")])
    @PutMapping("/{addressId}/default")
    fun setDefault(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable addressId: Long,
    ): ResponseEntity<Void> {
        shippingAddressUseCase.setDefaultAddress(userId, addressId)
        return ResponseEntity.ok().build()
    }
}
