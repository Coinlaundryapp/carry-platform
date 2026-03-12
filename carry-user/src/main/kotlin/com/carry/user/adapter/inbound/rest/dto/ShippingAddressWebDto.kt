package com.carry.user.adapter.inbound.rest.dto

import com.carry.user.domain.model.ShippingAddress
import com.carry.user.domain.vo.Address
import com.carry.user.domain.vo.Coordinates
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "배송지 등록 요청")
data class CreateShippingAddressRequest(
    @Schema(description = "배송지 별칭", example = "우리집")
    @field:NotBlank val alias: String,
    @Schema(description = "도로명 주소", example = "서울시 강남구 테헤란로 123")
    @field:NotBlank val roadAddress: String,
    @Schema(description = "상세 주소", example = "101동 1201호")
    @field:NotBlank val detailAddress: String,
    @Schema(description = "우편번호", example = "06234")
    @field:NotBlank val zipCode: String,
    @Schema(description = "위도", example = "37.5665")
    val latitude: Double,
    @Schema(description = "경도", example = "126.9780")
    val longitude: Double,
    @Schema(description = "수령인 이름", example = "홍길동")
    @field:NotBlank val recipientName: String,
    @Schema(description = "수령인 전화번호", example = "010-1234-5678")
    @field:NotBlank val recipientPhone: String,
    @Schema(description = "출입 정보", example = "비밀번호 1234#", nullable = true)
    val entranceInfo: String? = null,
    @Schema(description = "서비스 권역 코드", example = "GANGNAM")
    @field:NotBlank val areaCode: String,
) {
    fun toAddress() = Address(roadAddress, detailAddress, zipCode)
    fun toCoordinates() = Coordinates(latitude, longitude)
}

@Schema(description = "배송지 수정 요청")
data class UpdateShippingAddressRequest(
    @Schema(description = "배송지 별칭", example = "우리집")
    @field:NotBlank val alias: String,
    @Schema(description = "도로명 주소", example = "서울시 강남구 테헤란로 123")
    @field:NotBlank val roadAddress: String,
    @Schema(description = "상세 주소", example = "101동 1201호")
    @field:NotBlank val detailAddress: String,
    @Schema(description = "우편번호", example = "06234")
    @field:NotBlank val zipCode: String,
    @Schema(description = "위도", example = "37.5665")
    val latitude: Double,
    @Schema(description = "경도", example = "126.9780")
    val longitude: Double,
    @Schema(description = "수령인 이름", example = "홍길동")
    @field:NotBlank val recipientName: String,
    @Schema(description = "수령인 전화번호", example = "010-1234-5678")
    @field:NotBlank val recipientPhone: String,
    @Schema(description = "출입 정보", example = "비밀번호 1234#", nullable = true)
    val entranceInfo: String? = null,
    @Schema(description = "서비스 권역 코드", example = "GANGNAM")
    @field:NotBlank val areaCode: String,
) {
    fun toAddress() = Address(roadAddress, detailAddress, zipCode)
    fun toCoordinates() = Coordinates(latitude, longitude)
}

@Schema(description = "배송지 응답")
data class ShippingAddressResponse(
    @Schema(description = "배송지 ID") val id: Long,
    @Schema(description = "별칭") val alias: String,
    @Schema(description = "도로명 주소") val roadAddress: String,
    @Schema(description = "상세 주소") val detailAddress: String,
    @Schema(description = "우편번호") val zipCode: String,
    @Schema(description = "위도") val latitude: Double,
    @Schema(description = "경도") val longitude: Double,
    @Schema(description = "수령인 이름") val recipientName: String,
    @Schema(description = "수령인 전화번호") val recipientPhone: String,
    @Schema(description = "출입 정보", nullable = true) val entranceInfo: String?,
    @Schema(description = "서비스 권역 코드") val areaCode: String,
    @Schema(description = "기본 배송지 여부") val isDefault: Boolean,
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
