package com.carry.laundromat.adapter.inbound.rest.dto

import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.model.NearbyLaundromat
import com.carry.laundromat.domain.vo.LaundromatAddress
import com.carry.laundromat.domain.vo.LaundromatOption
import com.carry.laundromat.domain.vo.Location
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "세탁소 등록 요청")
data class RegisterLaundromatRequest(
    @Schema(description = "세탁소 이름", example = "클린세탁")
    @field:NotBlank val name: String,
    @Schema(description = "도로명 주소", example = "서울시 강남구 테헤란로 123")
    @field:NotBlank val roadAddress: String,
    @Schema(description = "상세 주소", nullable = true)
    val detailAddress: String? = null,
    @Schema(description = "우편번호", nullable = true)
    val zipCode: String? = null,
    @Schema(description = "위도", example = "37.5665")
    val latitude: Double,
    @Schema(description = "경도", example = "126.9780")
    val longitude: Double,
    @Schema(description = "세탁소 옵션 목록")
    val options: Set<LaundromatOption> = emptySet(),
) {
    fun toAddress() = LaundromatAddress(roadAddress, detailAddress, zipCode)
    fun toLocation() = Location(latitude, longitude)
}

@Schema(description = "세탁소 정보 수정 요청")
data class UpdateLaundromatInfoRequest(
    @Schema(description = "세탁소 이름", example = "클린세탁")
    @field:NotBlank val name: String,
    @Schema(description = "도로명 주소", example = "서울시 강남구 테헤란로 123")
    @field:NotBlank val roadAddress: String,
    @Schema(description = "상세 주소", nullable = true)
    val detailAddress: String? = null,
    @Schema(description = "우편번호", nullable = true)
    val zipCode: String? = null,
    @Schema(description = "위도", example = "37.5665")
    val latitude: Double,
    @Schema(description = "경도", example = "126.9780")
    val longitude: Double,
) {
    fun toAddress() = LaundromatAddress(roadAddress, detailAddress, zipCode)
    fun toLocation() = Location(latitude, longitude)
}

@Schema(description = "세탁소 옵션 수정 요청")
data class UpdateOptionsRequest(
    @Schema(description = "옵션 목록")
    val options: Set<LaundromatOption>,
)

@Schema(description = "이미지 추가 요청")
data class AddMediaResourceRequest(
    @Schema(description = "이미지 URL")
    @field:NotBlank val url: String,
    @Schema(description = "파일 확장자", example = "jpg")
    @field:NotBlank val extension: String,
)

@Schema(description = "이미지 응답")
data class MediaResourceResponse(
    @Schema(description = "이미지 ID") val id: Long,
    @Schema(description = "이미지 URL") val url: String,
    @Schema(description = "파일 확장자") val extension: String,
)

@Schema(description = "세탁소 응답")
data class LaundromatResponse(
    @Schema(description = "세탁소 ID") val id: Long,
    @Schema(description = "세탁소 이름") val name: String,
    @Schema(description = "도로명 주소") val roadAddress: String,
    @Schema(description = "상세 주소", nullable = true) val detailAddress: String?,
    @Schema(description = "우편번호", nullable = true) val zipCode: String?,
    @Schema(description = "위도") val latitude: Double,
    @Schema(description = "경도") val longitude: Double,
    @Schema(description = "옵션 목록") val options: Set<LaundromatOption>,
    @Schema(description = "이미지 목록") val mediaResources: List<MediaResourceResponse>,
) {
    companion object {
        fun from(laundromat: Laundromat) = LaundromatResponse(
            id = laundromat.id!!,
            name = laundromat.name,
            roadAddress = laundromat.address.roadAddress,
            detailAddress = laundromat.address.detailAddress,
            zipCode = laundromat.address.zipCode,
            latitude = laundromat.location.latitude,
            longitude = laundromat.location.longitude,
            options = laundromat.options,
            mediaResources = laundromat.mediaResources.map {
                MediaResourceResponse(it.id!!, it.url, it.extension)
            },
        )
    }
}

@Schema(description = "주변 세탁소 응답")
data class NearbyLaundromatResponse(
    @Schema(description = "세탁소 정보") val laundromat: LaundromatResponse,
    @Schema(description = "거리(미터)") val distanceMeters: Double,
) {
    companion object {
        fun from(nearby: NearbyLaundromat) = NearbyLaundromatResponse(
            laundromat = LaundromatResponse.from(nearby.laundromat),
            distanceMeters = nearby.distanceMeters,
        )
    }
}
