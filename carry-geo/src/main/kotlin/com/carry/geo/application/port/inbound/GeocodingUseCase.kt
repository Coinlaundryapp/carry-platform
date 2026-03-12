package com.carry.geo.application.port.inbound

import com.carry.geo.domain.model.GeocodingResult

interface GeocodingUseCase {

    fun geocode(address: String): List<GeocodingResult>

    fun geocodeJibun(address: String): List<GeocodingResult>

    fun geocodeRoad(address: String): List<GeocodingResult>
}
