package com.carry.geo.application.port.outbound

import com.carry.geo.domain.model.GeocodingResult

interface GeocodingPort {

    fun geocode(address: String): List<GeocodingResult>
}
