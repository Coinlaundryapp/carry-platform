package com.carry.geo.adapter.outbound.config

import com.carry.common.metrics.MetricsPort
import com.carry.geo.adapter.outbound.cache.RedisCachingGeocodingAdapter
import com.carry.geo.adapter.outbound.cache.RedisCachingReverseGeocodingAdapter
import com.carry.geo.adapter.outbound.external.naver.NaverApiProperties
import com.carry.geo.adapter.outbound.external.naver.NaverGeocodingAdapter
import com.carry.geo.adapter.outbound.external.naver.NaverReverseGeocodingAdapter
import com.carry.geo.adapter.outbound.resilience.CircuitBreakerGeocodingAdapter
import com.carry.geo.adapter.outbound.resilience.CircuitBreakerReverseGeocodingAdapter
import com.carry.geo.application.port.outbound.GeocodingPort
import com.carry.geo.application.port.outbound.ReverseGeocodingPort
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisTemplate

/**
 * 지오코딩 포트의 데코레이터 체인을 명시적으로 와이어링한다.
 *
 * 호출 순서 (Redis 사용 가능 시): `RedisCachingAdapter` → `CircuitBreakerAdapter` → `NaverAdapter`
 *
 * - 외층 캐시: cache HIT 시 CB·외부 호출 모두 스킵. 24h TTL 동안 외부 장애가 발생해도
 *   기 조회된 주소는 정상 응답.
 * - 중층 CB: 외부 API 장애 시 fast-fail로 스레드 블로킹·연쇄 장애 차단.
 * - 내층 Naver: 실제 HTTP 호출.
 *
 * forward/reverse는 같은 Naver 서비스를 사용하지만 [CircuitBreaker] 인스턴스를 분리
 * (`geocoding-forward`, `geocoding-reverse`)해 한쪽 장애가 다른 쪽 호출을 차단하지 않게 한다.
 * 공통 설정은 application.yml의 `resilience4j.circuitbreaker.configs.geocoding`을 공유한다.
 *
 * [RedisTemplate]이 등록되지 않은 환경(테스트에서 `RedisAutoConfiguration`을 exclude한 경우 등)
 * 에서는 캐시 층을 생략하고 CB만 적용해 graceful degradation한다. 이 fallback은 의도된 동작이며
 * 로그로 명시한다.
 */
@Configuration
class GeocodingResilienceConfig(
    private val naverProperties: NaverApiProperties,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val metrics: MetricsPort,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun geocodingPort(
        @Autowired(required = false) redisTemplate: RedisTemplate<String, Any>?,
    ): GeocodingPort {
        val raw = NaverGeocodingAdapter(naverProperties)
        val cb = circuitBreakerRegistry.circuitBreaker("geocoding-forward", "geocoding")
        val protected_ = CircuitBreakerGeocodingAdapter(raw, cb)
        return if (redisTemplate != null) {
            RedisCachingGeocodingAdapter(protected_, redisTemplate, metrics)
        } else {
            log.warn("RedisTemplate not available — GeocodingPort runs without cache layer (CB only)")
            protected_
        }
    }

    @Bean
    fun reverseGeocodingPort(
        @Autowired(required = false) redisTemplate: RedisTemplate<String, Any>?,
    ): ReverseGeocodingPort {
        val raw = NaverReverseGeocodingAdapter(naverProperties)
        val cb = circuitBreakerRegistry.circuitBreaker("geocoding-reverse", "geocoding")
        val protected_ = CircuitBreakerReverseGeocodingAdapter(raw, cb)
        return if (redisTemplate != null) {
            RedisCachingReverseGeocodingAdapter(protected_, redisTemplate, metrics)
        } else {
            log.warn("RedisTemplate not available — ReverseGeocodingPort runs without cache layer (CB only)")
            protected_
        }
    }
}
