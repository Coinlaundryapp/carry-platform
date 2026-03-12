package com.carry.geo.adapter.inbound.rest.dto

import com.carry.geo.domain.model.GeocodingResult
import com.carry.geo.domain.model.ReverseGeocodingResult
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "지오코딩 결과")
data class GeocodingResponse(
    @Schema(description = "지번 주소") val jibunAddress: String,
    @Schema(description = "도로명 주소") val roadAddress: String,
    @Schema(description = "위도", example = "37.5665") val latitude: Double,
    @Schema(description = "경도", example = "126.9780") val longitude: Double,
    @Schema(description = "시/도", nullable = true) val sido: String?,
    @Schema(description = "시/군/구", nullable = true) val sigungu: String?,
    @Schema(description = "동/면", nullable = true) val dongmyun: String?,
    @Schema(description = "리", nullable = true) val ri: String?,
    @Schema(description = "도로명", nullable = true) val roadName: String?,
    @Schema(description = "건물명", nullable = true) val buildingName: String?,
    @Schema(description = "지번", nullable = true) val landNumber: String?,
    @Schema(description = "우편번호", nullable = true) val postalCode: String?,
) {
    companion object {
        fun from(result: GeocodingResult) = GeocodingResponse(
            jibunAddress = result.jibunAddress,
            roadAddress = result.roadAddress,
            latitude = result.coordinate.latitude,
            longitude = result.coordinate.longitude,
            sido = result.addressComponent.sido,
            sigungu = result.addressComponent.sigungu,
            dongmyun = result.addressComponent.dongmyun,
            ri = result.addressComponent.ri,
            roadName = result.addressComponent.roadName,
            buildingName = result.addressComponent.buildingName,
            landNumber = result.addressComponent.landNumber,
            postalCode = result.addressComponent.postalCode,
        )
    }
}

@Schema(description = "역지오코딩 결과")
data class ReverseGeocodingResponse(
    @Schema(description = "국가") val country: String,
    @Schema(description = "시") val si: String,
    @Schema(description = "구") val gu: String,
    @Schema(description = "동") val dong: String,
    @Schema(description = "전체 주소") val fullAddress: String,
) {
    companion object {
        fun from(result: ReverseGeocodingResult) = ReverseGeocodingResponse(
            country = result.country,
            si = result.si,
            gu = result.gu,
            dong = result.dong,
            fullAddress = result.toFullAddress(),
        )
    }
}
