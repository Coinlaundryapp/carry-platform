package com.carry.geo.domain.model

import com.carry.geo.domain.vo.AddressComponent
import com.carry.geo.domain.vo.Coordinate

data class GeocodingResult(
    val jibunAddress: String,
    val roadAddress: String,
    val coordinate: Coordinate,
    val addressComponent: AddressComponent,
) {
    fun hasJibunAddress(): Boolean = jibunAddress.isNotBlank()

    fun hasRoadAddress(): Boolean = roadAddress.isNotBlank()
}
