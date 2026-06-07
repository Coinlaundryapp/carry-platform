package com.carry.user.adapter.outbound.auth

import com.carry.user.application.port.outbound.RefreshTokenStorePort
import com.carry.user.application.port.outbound.RotateResult
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.time.Duration

/**
 * [RefreshTokenStorePort]의 Redis 구현. 세션별 hash(`auth:refresh:{sid}`)에
 * `cur`/`prev`/`prevAt`을 담아 "현재 유효 jti" allowlist를 유지한다.
 *
 * 탐지+회전+폐기는 단일 Lua 스크립트로 **원자 실행**한다(TOCTOU·동시요청 self-lock 차단).
 * 직전 jti는 grace(ms) 이내 재시도를 정상으로 관용하되 `prevAt`을 갱신하지 않아 윈도우를 고정한다.
 *
 * ⚠️ **[StringRedisTemplate] 전용**: 공유 `RedisTemplate<String,Any>`는 값을 JSON 인용("uuid")해
 * Lua의 raw string 비교가 깨진다. raw string 직렬화가 필수.
 */
class RedisRefreshTokenStore(
    private val redis: StringRedisTemplate,
    private val ttlMillis: Long,
    private val graceMillis: Long,
) : RefreshTokenStorePort {

    override fun start(sessionId: String, jti: String) {
        val key = key(sessionId)
        redis.opsForHash<String, String>().put(key, FIELD_CUR, jti)
        redis.expire(key, Duration.ofMillis(ttlMillis))
    }

    override fun rotate(sessionId: String, presentedJti: String, newJti: String): RotateResult {
        val result = redis.execute(
            ROTATE_SCRIPT,
            listOf(key(sessionId)),
            presentedJti,
            newJti,
            ttlMillis.toString(),
            graceMillis.toString(),
        )
        return RotateResult.valueOf(result)
    }

    override fun delete(sessionId: String) {
        redis.delete(key(sessionId))
    }

    private fun key(sessionId: String) = "$KEY_PREFIX$sessionId"

    companion object {
        private const val KEY_PREFIX = "auth:refresh:"
        private const val FIELD_CUR = "cur"

        // KEYS[1]=세션 키, ARGV: [presentedJti, newJti, ttlMs, graceMs]
        // 반환: ROTATED | ABSENT | REUSE (spec §4.2)
        private val ROTATE_LUA = """
            local cur = redis.call('HGET', KEYS[1], 'cur')
            if not cur then return 'ABSENT' end
            local t = redis.call('TIME')
            local now = t[1] * 1000 + math.floor(t[2] / 1000)
            if cur == ARGV[1] then
              redis.call('HSET', KEYS[1], 'prev', cur, 'prevAt', now, 'cur', ARGV[2])
              redis.call('PEXPIRE', KEYS[1], ARGV[3])
              return 'ROTATED'
            end
            local prev = redis.call('HGET', KEYS[1], 'prev')
            if prev and prev == ARGV[1] then
              local prevAt = tonumber(redis.call('HGET', KEYS[1], 'prevAt'))
              if prevAt and (now - prevAt) <= tonumber(ARGV[4]) then
                redis.call('HSET', KEYS[1], 'prev', cur, 'cur', ARGV[2])
                redis.call('PEXPIRE', KEYS[1], ARGV[3])
                return 'ROTATED'
              end
            end
            redis.call('DEL', KEYS[1])
            return 'REUSE'
        """.trimIndent()

        private val ROTATE_SCRIPT = DefaultRedisScript(ROTATE_LUA, String::class.java)
    }
}
