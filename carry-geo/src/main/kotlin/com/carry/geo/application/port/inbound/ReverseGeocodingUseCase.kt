package com.carry.geo.application.port.inbound

import com.carry.geo.domain.model.ReverseGeocodingResult
import com.carry.geo.domain.vo.Coordinate

interface ReverseGeocodingUseCase {

    fun reverseGeocode(coordinate: Coordinate): ReverseGeocodingResult
}
