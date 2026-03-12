package com.carry.geo.adapter.inbound.rest.dto

import com.carry.geo.domain.model.GeocodingResult
import com.carry.geo.domain.model.ReverseGeocodingResult

data class GeocodingResponse(
    val jibunAddress: String,
    val roadAddress: String,
    val latitude: Double,
    val longitude: Double,
    val sido: String?,
    val sigungu: String?,
    val dongmyun: String?,
    val ri: String?,
    val roadName: String?,
    val buildingName: String?,
    val landNumber: String?,
    val postalCode: String?,
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

data class ReverseGeocodingResponse(
    val country: String,
    val si: String,
    val gu: String,
    val dong: String,
    val fullAddress: String,
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
