package com.carry.infra.redis

import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

/**
 * [IdempotencyStore] Redis 구현(분산 환경용). [keyPrefix]로 모듈 간 키공간을 분리한다.
 * 값을 모두 문자열로 다루므로(StringRedisTemplate 전용) 공유 RedisTemplate의 JSON 직렬화 모호성이 없다.
 *
 * - reserve: `SETNX` + 짧은 [pendingTtl](PENDING 마커). 동시/즉시 재시도를 한 번만 통과시킨다.
 *   PENDING TTL이 짧아 처리가 실패해도 그 키가 영원히 막히지 않는다.
 * - complete: 결과 id로 값을 교체하고 긴 [resultTtl]을 둬, 이후 동일 키 요청이 재생되게 한다.
 */
class RedisIdempotencyStore(
    private val redis: StringRedisTemplate,
    private val keyPrefix: String,
    private val pendingTtl: Duration,
    private val resultTtl: Duration,
) : IdempotencyStore {

    override fun reserve(key: String): Boolean =
        redis.opsForValue().setIfAbsent(fullKey(key), PENDING, pendingTtl) == true

    override fun findCompletedId(key: String): Long? =
        redis.opsForValue().get(fullKey(key))?.toLongOrNull() // PENDING → null, 부재 → null

    override fun complete(key: String, id: Long) {
        redis.opsForValue().set(fullKey(key), id.toString(), resultTtl)
    }

    private fun fullKey(key: String): String = "$keyPrefix$key"

    companion object {
        private const val PENDING = "PENDING"
    }
}
