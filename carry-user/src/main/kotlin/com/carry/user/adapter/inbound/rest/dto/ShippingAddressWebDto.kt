package com.carry.user.adapter.inbound.rest.dto

import com.carry.user.domain.model.ShippingAddress
import com.carry.user.domain.vo.Address
import com.carry.user.domain.vo.Coordinates
import jakarta.validation.constraints.NotBlank

data class CreateShippingAddressRequest(
    @field:NotBlank val alias: String,
    @field:NotBlank val roadAddress: String,
    @field:NotBlank val detailAddress: String,
    @field:NotBlank val zipCode: String,
    val latitude: Double,
    val longitude: Double,
    @field:NotBlank val recipientName: String,
    @field:NotBlank val recipientPhone: String,
    val entranceInfo: String? = null,
    @field:NotBlank val areaCode: String,
) {
    fun toAddress() = Address(roadAddress, detailAddress, zipCode)
    fun toCoordinates() = Coordinates(latitude, longitude)
}

data class UpdateShippingAddressRequest(
    @field:NotBlank val alias: String,
    @field:NotBlank val roadAddress: String,
    @field:NotBlank val detailAddress: String,
    @field:NotBlank val zipCode: String,
    val latitude: Double,
    val longitude: Double,
    @field:NotBlank val recipientName: String,
    @field:NotBlank val recipientPhone: String,
    val entranceInfo: String? = null,
    @field:NotBlank val areaCode: String,
) {
    fun toAddress() = Address(roadAddress, detailAddress, zipCode)
    fun toCoordinates() = Coordinates(latitude, longitude)
}

data class ShippingAddressResponse(
    val id: Long,
    val alias: String,
    val roadAddress: String,
    val detailAddress: String,
    val zipCode: String,
    val latitude: Double,
    val longitude: Double,
    val recipientName: String,
    val recipientPhone: String,
    val entranceInfo: String?,
    val areaCode: String,
    val isDefault: Boolean,
) {
    companion object {
        fun from(address: ShippingAddress) = ShippingAddressResponse(
            id = address.id!!,
            alias = address.alias,
            roadAddress = address.address.roadAddress,
            detailAddress = address.address.detailAddress,
            zipCode = address.address.zipCode,
            latitude = address.coordinates.latitude,
            longitude = address.coordinates.longitude,
            recipientName = address.recipientName,
            recipientPhone = address.recipientPhone,
            entranceInfo = address.entranceInfo,
            areaCode = address.areaCode,
            isDefault = address.isDefault,
        )
    }
}
