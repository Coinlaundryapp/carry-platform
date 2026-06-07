package com.carry.user.adapter.outbound.auth

import com.carry.user.application.port.outbound.RefreshTokenStorePort
import com.carry.user.application.port.outbound.RotateResult
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * [RefreshTokenStorePort]의 인메모리 구현. Redis가 없는 환경(테스트 프로파일은
 * `RedisAutoConfiguration` 제외)의 fallback이자 회전 알고리즘의 결정적 단위테스트 대상.
 *
 * ⚠️ 비분산 — 단일 JVM 메모리. 실 운영(다중 인스턴스)은 [RedisRefreshTokenStore]가 담당한다.
 * 회전/유예 의미는 Lua 구현과 **동등**해야 한다([rotate] 분기는 spec §4.2와 일치).
 * grace 판정 시각은 [clock]에서 읽어 테스트 결정성을 보장한다.
 */
class InMemoryRefreshTokenStore(
    private val graceMillis: Long,
    private val clock: Clock = Clock.systemUTC(),
) : RefreshTokenStorePort {

    private class Entry(var cur: String, var prev: String?, var prevAt: Long)

    private val sessions = ConcurrentHashMap<String, Entry>()
    private val lock = Any()

    override fun start(sessionId: String, jti: String) {
        synchronized(lock) {
            sessions[sessionId] = Entry(cur = jti, prev = null, prevAt = 0)
        }
    }

    override fun rotate(sessionId: String, presentedJti: String, newJti: String): RotateResult {
        synchronized(lock) {
            val e = sessions[sessionId] ?: return RotateResult.ABSENT
            val now = clock.millis()
            if (e.cur == presentedJti) {
                e.prev = e.cur
                e.prevAt = now
                e.cur = newJti
                return RotateResult.ROTATED
            }
            if (e.prev != null && e.prev == presentedJti && now - e.prevAt <= graceMillis) {
                // 유예 재시도: prev 값을 나가는 cur로 전진, prevAt(시계) 고정 → 윈도우 경계 유지 (spec §3.2)
                e.prev = e.cur
                e.cur = newJti
                return RotateResult.ROTATED
            }
            sessions.remove(sessionId)
            return RotateResult.REUSE
        }
    }

    override fun delete(sessionId: String) {
        synchronized(lock) {
            sessions.remove(sessionId)
        }
    }
}
