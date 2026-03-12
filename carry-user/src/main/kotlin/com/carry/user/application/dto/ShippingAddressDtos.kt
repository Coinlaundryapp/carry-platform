package com.carry.user.application.dto

import com.carry.user.domain.model.ShippingAddress

data class CreateShippingAddressCommand(
    val alias: String,
    val roadAddress: String,
    val detailAddress: String,
    val zipCode: String,
    val latitude: Double,
    val longitude: Double
)

data class UpdateShippingAddressCommand(
    val alias: String,
    val roadAddress: String,
    val detailAddress: String,
    val zipCode: String,
    val latitude: Double,
    val longitude: Double
)

data class ShippingAddressResponse(
    val id: Long,
    val alias: String,
    val roadAddress: String,
    val detailAddress: String,
    val zipCode: String,
    val latitude: Double,
    val longitude: Double,
    val isDefault: Boolean
) {
    companion object {
        fun from(address: ShippingAddress) = ShippingAddressResponse(
            id = address.id,
            alias = address.alias,
            roadAddress = address.roadAddress,
            detailAddress = address.detailAddress,
            zipCode = address.zipCode,
            latitude = address.latitude,
            longitude = address.longitude,
            isDefault = address.isDefault
        )
    }
}
