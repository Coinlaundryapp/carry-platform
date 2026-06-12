package com.carry.geo.adapter.outbound.cache

import com.carry.common.metrics.MetricsPort
import com.carry.geo.application.port.outbound.GeocodingPort
import com.carry.geo.domain.model.GeocodingResult
import org.springframework.data.redis.core.RedisTemplate
import java.time.Duration

/**
 * [GeocodingPort] 데코레이터 — Redis에 24h TTL로 결과를 캐싱해 같은 주소에 대한 외부
 * API 호출을 줄이고, 외부 API 장애 시 최근 24h 내 조회된 주소는 자연스럽게 fallback된다.
 *
 * - cache HIT: delegate를 호출하지 않고 캐시 값 즉시 반환 → Circuit Breaker도 거치지 않음
 * - cache MISS: delegate 호출 후 비어있지 않은 결과만 캐시 (음수 캐시 방지)
 * - delegate 예외: 캐시하지 않고 그대로 전파
 * - cache key 정규화: 주소 양쪽 공백 trim
 */
class RedisCachingGeocodingAdapter(
    private val delegate: GeocodingPort,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val metrics: MetricsPort,
    private val ttl: Duration = Duration.ofHours(24),
) : GeocodingPort {

    override fun geocode(address: String): List<GeocodingResult> {
        val key = cacheKey(address)
        val cached = redisTemplate.opsForValue().get(key)
        if (cached != null) {
            metrics.incrementCounter(CACHE_METRIC, "direction" to "fwd", "result" to "hit")
            @Suppress("UNCHECKED_CAST")
            return cached as List<GeocodingResult>
        }
        metrics.incrementCounter(CACHE_METRIC, "direction" to "fwd", "result" to "miss")
        val fresh = delegate.geocode(address)
        if (fresh.isNotEmpty()) {
            redisTemplate.opsForValue().set(key, fresh, ttl)
        }
        return fresh
    }

    private fun cacheKey(address: String): String = "$KEY_PREFIX${address.trim()}"

    companion object {
        private const val KEY_PREFIX = "geo:fwd:"

        /** 캐시 적중률 카운터 — Grafana "Geo 캐시 적중률" 패널이 소비한다. 태그: direction(fwd/rev), result(hit/miss). */
        const val CACHE_METRIC = "carry.geo.cache"
    }
}
