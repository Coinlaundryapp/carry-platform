package com.carry.order.adapter.outbound.redis

import com.carry.order.application.port.outbound.IdempotencyPort
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

/**
 * [IdempotencyPort] 의 Redis 구현(분산 환경용). 와이어링은 [IdempotencyStoreConfig] 가 담당한다.
 *
 * - reserve: `SETNX` + 짧은 TTL(PENDING 마커). 동시/즉시 재시도를 한 번만 통과시킨다.
 * - complete: 결과 orderId 로 값을 교체하고 긴 TTL 을 둬, 이후 동일 키 요청이 재생되게 한다.
 * - PENDING TTL 을 짧게 둠으로써, 처리가 실패해도 그 키가 영원히 막히지 않는다.
 *
 * 값을 모두 문자열로 다루므로 공유 `RedisTemplate<String,Any>`의 JSON 직렬화 모호성이 없다
 * ([StringRedisTemplate] 전용).
 */
class RedisIdempotencyAdapter(
    private val redis: StringRedisTemplate,
    private val pendingTtl: Duration,
    private val resultTtl: Duration,
) : IdempotencyPort {

    override fun reserve(key: String): Boolean =
        redis.opsForValue().setIfAbsent(fullKey(key), PENDING, pendingTtl) == true

    override fun findCompletedOrderId(key: String): Long? =
        redis.opsForValue().get(fullKey(key))?.toLongOrNull() // PENDING → null, 부재 → null

    override fun complete(key: String, orderId: Long) {
        redis.opsForValue().set(fullKey(key), orderId.toString(), resultTtl)
    }

    private fun fullKey(key: String): String = "$KEY_PREFIX$key"

    companion object {
        private const val KEY_PREFIX = "idem:order:create:"
        private const val PENDING = "PENDING"
    }
}
