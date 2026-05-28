package com.carry.geo.adapter.outbound.external.naver

import com.carry.geo.adapter.outbound.external.naver.dto.NaverGeocodingResponse
import com.carry.geo.application.port.outbound.GeocodingPort
import com.carry.geo.domain.exception.GeocodingFailedException
import com.carry.geo.domain.model.GeocodingResult
import org.springframework.web.client.RestClient

/**
 * Naver 지오코딩 API 호출 — Circuit Breaker + Redis 캐싱 데코레이터로 감싸 노출되므로
 * @Component가 아닌 [com.carry.geo.adapter.outbound.config.GeocodingResilienceConfig]에서
 * 명시적으로 빈 등록된다.
 */
class NaverGeocodingAdapter(
    properties: NaverApiProperties,
) : GeocodingPort {

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(properties.geocodingUrl)
        .defaultHeader("X-NCP-APIGW-API-KEY-ID", properties.clientId)
        .defaultHeader("X-NCP-APIGW-API-KEY", properties.clientSecret)
        .build()

    override fun geocode(address: String): List<GeocodingResult> {
        val response = try {
            restClient.get()
                .uri { it.queryParam("query", address).build() }
                .retrieve()
                .body(NaverGeocodingResponse::class.java)
        } catch (e: Exception) {
            throw GeocodingFailedException("네이버 지오코딩 API 호출에 실패했습니다: ${e.message}", e)
        }

        return response?.addresses?.map { it.toDomain() } ?: emptyList()
    }
}
