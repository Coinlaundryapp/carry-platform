package com.carry.user.presentation.rest

import com.carry.common.response.ApiResponse
import com.carry.user.application.dto.CreateShippingAddressCommand
import com.carry.user.application.dto.ShippingAddressResponse
import com.carry.user.application.dto.UpdateShippingAddressCommand
import com.carry.user.application.service.ShippingAddressService
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
    private val shippingAddressService: ShippingAddressService
) {

    @GetMapping
    fun getMyAddresses(@AuthenticationPrincipal userId: Long): ResponseEntity<ApiResponse<List<ShippingAddressResponse>>> {
        return ResponseEntity.ok(ApiResponse.success(shippingAddressService.getAddresses(userId)))
    }

    @PostMapping
    fun createAddress(
        @AuthenticationPrincipal userId: Long,
        @RequestBody command: CreateShippingAddressCommand
    ): ResponseEntity<ApiResponse<ShippingAddressResponse>> {
        val address = shippingAddressService.createAddress(userId, command)
        return ResponseEntity.status(201).body(ApiResponse.created(address))
    }

    @PutMapping("/{addressId}")
    fun updateAddress(
        @AuthenticationPrincipal userId: Long,
        @PathVariable addressId: Long,
        @RequestBody command: UpdateShippingAddressCommand
    ): ResponseEntity<ApiResponse<ShippingAddressResponse>> {
        val address = shippingAddressService.updateAddress(userId, addressId, command)
        return ResponseEntity.ok(ApiResponse.success(address))
    }

    @DeleteMapping("/{addressId}")
    fun deleteAddress(
        @AuthenticationPrincipal userId: Long,
        @PathVariable addressId: Long
    ): ResponseEntity<Void> {
        shippingAddressService.deleteAddress(userId, addressId)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{addressId}/default")
    fun setDefault(
        @AuthenticationPrincipal userId: Long,
        @PathVariable addressId: Long
    ): ResponseEntity<Void> {
        shippingAddressService.setDefaultAddress(userId, addressId)
        return ResponseEntity.ok().build()
    }
}
