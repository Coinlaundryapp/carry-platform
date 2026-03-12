package com.carry.geo.application.port.outbound

import com.carry.geo.domain.model.ReverseGeocodingResult
import com.carry.geo.domain.vo.Coordinate

interface ReverseGeocodingPort {

    fun reverseGeocode(coordinate: Coordinate): ReverseGeocodingResult
}
