package com.carry.user.adapter.outbound.auth

import com.carry.user.application.port.outbound.RotateResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * 회전 알고리즘의 결정적 검증. [RedisRefreshTokenStore]의 Lua와 **동일 의미**여야 하며
 * (spec §4.2·§3.2), 동일 시나리오 매트릭스를 Testcontainers Redis 통합테스트에도 적용한다.
 */
class InMemoryRefreshTokenStoreTest {

    private val grace = 10_000L

    /** 테스트가 제어하는 가변 시계. */
    private class MutableClock(var nowMillis: Long) : Clock() {
        override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
    }

    private fun store(clock: Clock) = InMemoryRefreshTokenStore(grace, clock)

    @Test
    fun `현재 jti로 회전하면 ROTATED`() {
        val sut = store(MutableClock(0))
        sut.start("s", "j1")

        assertThat(sut.rotate("s", "j1", "j2")).isEqualTo(RotateResult.ROTATED)
    }

    @Test
    fun `없는 세션은 ABSENT`() {
        val sut = store(MutableClock(0))

        assertThat(sut.rotate("nope", "j1", "j2")).isEqualTo(RotateResult.ABSENT)
    }

    @Test
    fun `delete 후 회전은 ABSENT`() {
        val sut = store(MutableClock(0))
        sut.start("s", "j1")
        sut.delete("s")

        assertThat(sut.rotate("s", "j1", "j2")).isEqualTo(RotateResult.ABSENT)
    }

    @Test
    fun `유예 초과한 옛 jti 재사용은 REUSE이고 세션을 폐기한다`() {
        val clock = MutableClock(0)
        val sut = store(clock)
        sut.start("s", "j1")
        sut.rotate("s", "j1", "j2") // j1 → prev, prevAt=0
        clock.nowMillis = grace + 1 // 유예 만료

        assertThat(sut.rotate("s", "j1", "j3")).isEqualTo(RotateResult.REUSE)
        // 세션 폐기 확인 — 현재 jti(j2)로도 더 이상 회전 불가
        assertThat(sut.rotate("s", "j2", "j4")).isEqualTo(RotateResult.ABSENT)
    }

    @Test
    fun `유예 내 직전 jti 재시도는 ROTATED (정상 재시도 관용)`() {
        val clock = MutableClock(0)
        val sut = store(clock)
        sut.start("s", "j1")
        sut.rotate("s", "j1", "j2") // prev=j1, prevAt=0
        clock.nowMillis = grace - 1 // 유예 내

        assertThat(sut.rotate("s", "j1", "j3")).isEqualTo(RotateResult.ROTATED)
    }

    @Test
    fun `덮인 cur를 유예 내 제시해도 ROTATED (동시 2요청 회귀)`() {
        // Req1: j1→j2 (반환 j2). Req2(같은 j1 재시도): 유예로 j2→j3, prev←j2.
        // 클라가 j2를 보관했다 제시 → REUSE가 아니라 ROTATED여야 한다(spec v2 #1).
        val clock = MutableClock(0)
        val sut = store(clock)
        sut.start("s", "j1")
        sut.rotate("s", "j1", "j2")            // prev=j1, cur=j2, prevAt=0
        sut.rotate("s", "j1", "j3")            // 유예 재시도: prev=j2(값 전진), cur=j3, prevAt 고정=0

        assertThat(sut.rotate("s", "j2", "j4")).isEqualTo(RotateResult.ROTATED)
    }

    @Test
    fun `유예 윈도우는 슬라이딩하지 않는다 (prevAt 고정)`() {
        // 유예 재시도(prev=j1 제시)가 prev 값만 전진(→j2)시키고 prevAt(=0)은 고정하므로,
        // grace 경계는 첫 회전 기준으로 만료된다.
        val clock = MutableClock(0)
        val sut = store(clock)
        sut.start("s", "j1")
        sut.rotate("s", "j1", "j2")            // prev=j1, cur=j2, prevAt=0
        clock.nowMillis = grace - 1
        sut.rotate("s", "j1", "j3")            // 유예 재시도(prev=j1 제시): prev←j2, cur=j3, prevAt 여전히 0
        clock.nowMillis = grace + 1            // 첫 회전 기준 만료

        assertThat(sut.rotate("s", "j2", "j4")).isEqualTo(RotateResult.REUSE)
    }
}
