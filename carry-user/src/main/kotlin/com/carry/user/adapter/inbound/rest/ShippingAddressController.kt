package com.carry.user.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.user.adapter.inbound.rest.dto.CreateShippingAddressRequest
import com.carry.user.adapter.inbound.rest.dto.ShippingAddressResponse
import com.carry.user.adapter.inbound.rest.dto.UpdateShippingAddressRequest
import com.carry.user.application.port.inbound.ShippingAddressUseCase
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/shipping-addresses")
class ShippingAddressController(
    private val shippingAddressUseCase: ShippingAddressUseCase,
) {

    @GetMapping
    fun getMyAddresses(
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<List<ShippingAddressResponse>>> {
        val addresses = shippingAddressUseCase.getAddresses(userId)
            .map { ShippingAddressResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.success(addresses))
    }

    @PostMapping
    fun createAddress(
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: CreateShippingAddressRequest,
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
        return ResponseEntity.status(201).body(ApiResponse.created(ShippingAddressResponse.from(address)))
    }

    @PutMapping("/{addressId}")
    fun updateAddress(
        @AuthenticationPrincipal userId: Long,
        @PathVariable addressId: Long,
        @RequestBody request: UpdateShippingAddressRequest,
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

    @DeleteMapping("/{addressId}")
    fun deleteAddress(
        @AuthenticationPrincipal userId: Long,
        @PathVariable addressId: Long,
    ): ResponseEntity<Void> {
        shippingAddressUseCase.deleteAddress(userId, addressId)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{addressId}/default")
    fun setDefault(
        @AuthenticationPrincipal userId: Long,
        @PathVariable addressId: Long,
    ): ResponseEntity<Void> {
        shippingAddressUseCase.setDefaultAddress(userId, addressId)
        return ResponseEntity.ok().build()
    }
}
