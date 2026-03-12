package com.carry.geo.application.service

import com.carry.geo.application.port.inbound.GeocodingUseCase
import com.carry.geo.application.port.outbound.GeocodingPort
import com.carry.geo.domain.model.GeocodingResult
import org.springframework.stereotype.Service

@Service
class GeocodingService(
    private val geocodingPort: GeocodingPort,
) : GeocodingUseCase {

    override fun geocode(address: String): List<GeocodingResult> {
        require(address.isNotBlank()) { "주소는 비어있을 수 없습니다" }
        return geocodingPort.geocode(address)
    }

    override fun geocodeJibun(address: String): List<GeocodingResult> {
        return geocode(address).filter { it.hasJibunAddress() }
    }

    override fun geocodeRoad(address: String): List<GeocodingResult> {
        return geocode(address).filter { it.hasRoadAddress() }
    }
}
