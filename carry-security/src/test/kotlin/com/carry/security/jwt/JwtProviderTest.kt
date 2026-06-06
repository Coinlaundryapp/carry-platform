package com.carry.security.jwt

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtProviderTest {

    private val properties = JwtProperties(
        secret = "test-secret-test-secret-test-secret-0123456789",
        accessTokenExpiration = 3_600_000,
        refreshTokenExpiration = 604_800_000,
    )
    private val sut = JwtProvider(properties)

    @Test
    fun `액세스 토큰을 파싱하면 userId와 role을 반환한다`() {
        val token = sut.createAccessToken(42L, "COORDINATOR")

        val principal = sut.parseToken(token)

        assertThat(principal).isNotNull
        assertThat(principal!!.userId).isEqualTo(42L)
        // role claim 문자열 계약: bare UserRole 이름이 그대로 보존되어야 한다 (ROLE_ 접두는 필터가 부여).
        assertThat(principal.role).isEqualTo("COORDINATOR")
    }

    @Test
    fun `role claim이 없는 토큰(리프레시)은 기본 역할 CUSTOMER로 파싱된다`() {
        val token = sut.createRefreshToken(7L)

        val principal = sut.parseToken(token)

        assertThat(principal).isNotNull
        assertThat(principal!!.userId).isEqualTo(7L)
        assertThat(principal.role).isEqualTo("CUSTOMER")
    }

    @Test
    fun `위조된 토큰은 null을 반환한다`() {
        val principal = sut.parseToken("not-a-valid-token")

        assertThat(principal).isNull()
    }

    @Test
    fun `만료된 토큰은 null을 반환한다`() {
        val expiredProvider = JwtProvider(properties.copy(accessTokenExpiration = -1_000))
        val token = expiredProvider.createAccessToken(1L, "CUSTOMER")

        val principal = sut.parseToken(token)

        assertThat(principal).isNull()
    }
}
