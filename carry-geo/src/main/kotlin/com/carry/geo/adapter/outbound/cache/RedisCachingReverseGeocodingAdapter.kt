package com.carry.geo.adapter.outbound.cache

import com.carry.common.metrics.MetricsPort
import com.carry.geo.application.port.outbound.ReverseGeocodingPort
import com.carry.geo.domain.model.ReverseGeocodingResult
import com.carry.geo.domain.vo.Coordinate
import org.springframework.data.redis.core.RedisTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/**
 * [ReverseGeocodingPort] 데코레이터 — Redis에 24h TTL로 좌표→주소 결과를 캐싱한다.
 *
 * - cache HIT: delegate를 호출하지 않고 즉시 반환
 * - cache MISS: delegate 호출 후 결과 캐시
 * - delegate 예외: 캐시하지 않고 그대로 전파
 * - cache key 정규화: 좌표를 소수점 6자리로 반올림 (약 11cm 단위) → 미세한 부동소수 차이로
 *   인한 키 폭발 방지. 일반적인 위치 기반 조회 정밀도로 충분.
 */
class RedisCachingReverseGeocodingAdapter(
    private val delegate: ReverseGeocodingPort,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val metrics: MetricsPort,
    private val ttl: Duration = Duration.ofHours(24),
) : ReverseGeocodingPort {

    override fun reverseGeocode(coordinate: Coordinate): ReverseGeocodingResult {
        val key = cacheKey(coordinate)
        val cached = redisTemplate.opsForValue().get(key)
        if (cached != null) {
            metrics.incrementCounter(RedisCachingGeocodingAdapter.CACHE_METRIC, "direction" to "rev", "result" to "hit")
            return cached as ReverseGeocodingResult
        }
        metrics.incrementCounter(RedisCachingGeocodingAdapter.CACHE_METRIC, "direction" to "rev", "result" to "miss")
        val fresh = delegate.reverseGeocode(coordinate)
        redisTemplate.opsForValue().set(key, fresh, ttl)
        return fresh
    }

    private fun cacheKey(coordinate: Coordinate): String {
        val lat = round6(coordinate.latitude)
        val lon = round6(coordinate.longitude)
        return "$KEY_PREFIX$lat:$lon"
    }

    private fun round6(value: Double): String =
        BigDecimal(value).setScale(COORD_SCALE, RoundingMode.HALF_UP).toPlainString()

    companion object {
        private const val KEY_PREFIX = "geo:rev:"
        private const val COORD_SCALE = 6
    }
}
