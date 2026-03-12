package com.carry.geo.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.geo.adapter.inbound.rest.dto.GeocodingResponse
import com.carry.geo.adapter.inbound.rest.dto.ReverseGeocodingResponse
import com.carry.geo.application.port.inbound.GeocodingUseCase
import com.carry.geo.application.port.inbound.ReverseGeocodingUseCase
import com.carry.geo.domain.vo.Coordinate
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/geo")
class GeoController(
    private val geocodingUseCase: GeocodingUseCase,
    private val reverseGeocodingUseCase: ReverseGeocodingUseCase,
) {

    @GetMapping("/geocode")
    fun geocode(
        @RequestParam address: String,
    ): ResponseEntity<ApiResponse<List<GeocodingResponse>>> {
        val results = geocodingUseCase.geocode(address)
        return ResponseEntity.ok(ApiResponse.success(results.map { GeocodingResponse.from(it) }))
    }

    @GetMapping("/geocode/jibun")
    fun geocodeJibun(
        @RequestParam address: String,
    ): ResponseEntity<ApiResponse<List<GeocodingResponse>>> {
        val results = geocodingUseCase.geocodeJibun(address)
        return ResponseEntity.ok(ApiResponse.success(results.map { GeocodingResponse.from(it) }))
    }

    @GetMapping("/geocode/road")
    fun geocodeRoad(
        @RequestParam address: String,
    ): ResponseEntity<ApiResponse<List<GeocodingResponse>>> {
        val results = geocodingUseCase.geocodeRoad(address)
        return ResponseEntity.ok(ApiResponse.success(results.map { GeocodingResponse.from(it) }))
    }

    @GetMapping("/reverse-geocode")
    fun reverseGeocode(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
    ): ResponseEntity<ApiResponse<ReverseGeocodingResponse>> {
        val coordinate = Coordinate(latitude, longitude)
        val result = reverseGeocodingUseCase.reverseGeocode(coordinate)
        return ResponseEntity.ok(ApiResponse.success(ReverseGeocodingResponse.from(result)))
    }
}
