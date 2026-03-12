package com.carry.geo.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.geo.adapter.inbound.rest.dto.GeocodingResponse
import com.carry.geo.adapter.inbound.rest.dto.ReverseGeocodingResponse
import com.carry.geo.application.port.inbound.GeocodingUseCase
import com.carry.geo.application.port.inbound.ReverseGeocodingUseCase
import com.carry.geo.domain.vo.Coordinate
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Geocoding", description = "지오코딩 API")
@RestController
@RequestMapping("/api/v2/geo")
class GeoController(
    private val geocodingUseCase: GeocodingUseCase,
    private val reverseGeocodingUseCase: ReverseGeocodingUseCase,
) {

    @Operation(summary = "주소 → 좌표 변환", description = "주소를 입력하여 좌표를 조회합니다 (통합 검색)")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "지오코딩 성공")])
    @GetMapping("/geocode")
    fun geocode(
        @Parameter(description = "검색할 주소", example = "서울시 강남구 테헤란로 123") @RequestParam address: String,
    ): ResponseEntity<ApiResponse<List<GeocodingResponse>>> {
        val results = geocodingUseCase.geocode(address)
        return ResponseEntity.ok(ApiResponse.success(results.map { GeocodingResponse.from(it) }))
    }

    @Operation(summary = "지번 주소 → 좌표 변환", description = "지번 주소를 입력하여 좌표를 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "지오코딩 성공")])
    @GetMapping("/geocode/jibun")
    fun geocodeJibun(
        @Parameter(description = "지번 주소", example = "서울시 강남구 역삼동 123-45") @RequestParam address: String,
    ): ResponseEntity<ApiResponse<List<GeocodingResponse>>> {
        val results = geocodingUseCase.geocodeJibun(address)
        return ResponseEntity.ok(ApiResponse.success(results.map { GeocodingResponse.from(it) }))
    }

    @Operation(summary = "도로명 주소 → 좌표 변환", description = "도로명 주소를 입력하여 좌표를 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "지오코딩 성공")])
    @GetMapping("/geocode/road")
    fun geocodeRoad(
        @Parameter(description = "도로명 주소", example = "서울시 강남구 테헤란로 123") @RequestParam address: String,
    ): ResponseEntity<ApiResponse<List<GeocodingResponse>>> {
        val results = geocodingUseCase.geocodeRoad(address)
        return ResponseEntity.ok(ApiResponse.success(results.map { GeocodingResponse.from(it) }))
    }

    @Operation(summary = "좌표 → 주소 변환", description = "위경도 좌표를 입력하여 주소를 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "역지오코딩 성공")])
    @GetMapping("/reverse-geocode")
    fun reverseGeocode(
        @Parameter(description = "위도", example = "37.5665") @RequestParam latitude: Double,
        @Parameter(description = "경도", example = "126.9780") @RequestParam longitude: Double,
    ): ResponseEntity<ApiResponse<ReverseGeocodingResponse>> {
        val coordinate = Coordinate(latitude, longitude)
        val result = reverseGeocodingUseCase.reverseGeocode(coordinate)
        return ResponseEntity.ok(ApiResponse.success(ReverseGeocodingResponse.from(result)))
    }
}
