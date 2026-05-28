package com.carry.geo.adapter.outbound.resilience

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.geo.application.port.outbound.GeocodingPort
import com.carry.geo.domain.model.GeocodingResult
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker

/**
 * [GeocodingPort] 데코레이터 — 외부 지오코딩 API 호출을 Resilience4j Circuit Breaker로
 * 감싸 장애 전파를 차단한다.
 *
 * - 정상 호출은 그대로 위임
 * - delegate가 던지는 예외는 그대로 전파(보호 대상이 아닌 도메인 흐름에서 의미 보존)
 * - Circuit이 OPEN 상태일 때는 [CallNotPermittedException]을 즉시 던지므로
 *   [BusinessException]([ErrorCode.GEOCODING_UNAVAILABLE])로 변환해 호출 측에 명확한
 *   재시도 신호를 준다.
 */
class CircuitBreakerGeocodingAdapter(
    private val delegate: GeocodingPort,
    private val circuitBreaker: CircuitBreaker,
) : GeocodingPort {

    override fun geocode(address: String): List<GeocodingResult> =
        try {
            circuitBreaker.executeSupplier { delegate.geocode(address) }
        } catch (e: CallNotPermittedException) {
            throw BusinessException(
                ErrorCode.GEOCODING_UNAVAILABLE,
                "${circuitBreaker.name} circuit OPEN — ${e.message}",
                e,
            )
        }
}
