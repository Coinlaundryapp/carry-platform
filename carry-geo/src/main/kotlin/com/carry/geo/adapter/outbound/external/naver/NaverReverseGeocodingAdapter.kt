package com.carry.geo.adapter.outbound.external.naver

import com.carry.geo.adapter.outbound.external.naver.dto.NaverReverseGeocodingResponse
import com.carry.geo.application.port.outbound.ReverseGeocodingPort
import com.carry.geo.domain.exception.ReverseGeocodingFailedException
import com.carry.geo.domain.model.ReverseGeocodingResult
import com.carry.geo.domain.vo.Coordinate
import org.springframework.web.client.RestClient

/**
 * Naver 역지오코딩 API 호출 — Circuit Breaker + Redis 캐싱 데코레이터로 감싸 노출되므로
 * @Component가 아닌 [com.carry.geo.adapter.outbound.config.GeocodingResilienceConfig]에서
 * 명시적으로 빈 등록된다.
 */
class NaverReverseGeocodingAdapter(
    properties: NaverApiProperties,
) : ReverseGeocodingPort {

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(properties.reverseGeocodingUrl)
        .defaultHeader("X-NCP-APIGW-API-KEY-ID", properties.clientId)
        .defaultHeader("X-NCP-APIGW-API-KEY", properties.clientSecret)
        .build()

    override fun reverseGeocode(coordinate: Coordinate): ReverseGeocodingResult {
        val response = try {
            restClient.get()
                .uri {
                    it.queryParam("coords", coordinate.toNaverCoordsFormat())
                        .queryParam("output", "json")
                        .queryParam("orders", "addr")
                        .queryParam("sourcecrs", "EPSG:4326")
                        .build()
                }
                .retrieve()
                .body(NaverReverseGeocodingResponse::class.java)
        } catch (e: Exception) {
            throw ReverseGeocodingFailedException(
                "네이버 역지오코딩 API 호출에 실패했습니다: ${e.message}", e,
            )
        }

        val result = response?.results?.firstOrNull()
            ?: throw ReverseGeocodingFailedException("역지오코딩 결과가 없습니다")

        return result.region.toDomain()
    }
}
