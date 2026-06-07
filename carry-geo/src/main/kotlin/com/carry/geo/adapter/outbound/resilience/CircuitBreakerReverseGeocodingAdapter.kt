package com.carry.geo.adapter.outbound.resilience

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.geo.application.port.outbound.ReverseGeocodingPort
import com.carry.geo.domain.model.ReverseGeocodingResult
import com.carry.geo.domain.vo.Coordinate
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker

/**
 * [ReverseGeocodingPort] 데코레이터 — 외부 역지오코딩 API 호출을 Resilience4j Circuit
 * Breaker로 감싸 장애 전파를 차단한다.
 *
 * - 정상 호출은 그대로 위임
 * - delegate가 던지는 예외는 그대로 전파
 * - Circuit이 OPEN 상태일 때는 [BusinessException]([ErrorCode.GEOCODING_UNAVAILABLE])로
 *   변환해 호출 측에 재시도 신호를 준다.
 */
class CircuitBreakerReverseGeocodingAdapter(
    private val delegate: ReverseGeocodingPort,
    private val circuitBreaker: CircuitBreaker,
) : ReverseGeocodingPort {

    override fun reverseGeocode(coordinate: Coordinate): ReverseGeocodingResult =
        try {
            circuitBreaker.executeSupplier { delegate.reverseGeocode(coordinate) }
        } catch (e: CallNotPermittedException) {
            throw BusinessException(
                ErrorCode.GEOCODING_UNAVAILABLE,
                "${circuitBreaker.name} circuit OPEN — ${e.message}",
                e,
            )
        }
}
