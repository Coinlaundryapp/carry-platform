package com.carry.geo.application.service

import com.carry.geo.application.port.inbound.ReverseGeocodingUseCase
import com.carry.geo.application.port.outbound.ReverseGeocodingPort
import com.carry.geo.domain.model.ReverseGeocodingResult
import com.carry.geo.domain.vo.Coordinate
import org.springframework.stereotype.Service

@Service
class ReverseGeocodingService(
    private val reverseGeocodingPort: ReverseGeocodingPort,
) : ReverseGeocodingUseCase {

    override fun reverseGeocode(coordinate: Coordinate): ReverseGeocodingResult {
        return reverseGeocodingPort.reverseGeocode(coordinate)
    }
}
