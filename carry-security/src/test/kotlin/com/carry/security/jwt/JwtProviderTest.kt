package com.carry.security.jwt

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtProviderTest {

    private val properties = JwtProperties(
        secret = "test-secret-test-secret-test-secret-0123456789",
        accessTokenExpiration = 3_600_000,
        refreshTokenExpiration = 604_800_000,
        signupTokenExpiration = 600_000,
    )
    private val sut = JwtProvider(properties)

    // --- parseToken (인증 필터 경로): ACCESS 토큰만 수용 ---

    @Test
    fun `ACCESS 토큰을 파싱하면 userId와 role을 반환한다`() {
        val token = sut.createAccessToken(42L, "COORDINATOR")

        val principal = sut.parseToken(token)

        assertThat(principal).isNotNull
        assertThat(principal!!.userId).isEqualTo(42L)
        // role claim 문자열 계약: bare UserRole 이름 보존 (ROLE_ 접두는 필터가 부여)
        assertThat(principal.role).isEqualTo("COORDINATOR")
    }

    @Test
    fun `parseToken은 REFRESH 토큰을 거부한다`() {
        val refresh = sut.createRefreshToken(7L, "sess-1", "jti-1")

        assertThat(sut.parseToken(refresh)).isNull()
    }

    @Test
    fun `parseToken은 SIGNUP 토큰을 거부한다`() {
        val signup = sut.createSignupToken("KAKAO", "kakao-1", null, null)

        assertThat(sut.parseToken(signup)).isNull()
    }

    @Test
    fun `parseToken은 위조 토큰에 null을 반환한다`() {
        assertThat(sut.parseToken("not-a-valid-token")).isNull()
    }

    @Test
    fun `parseToken은 만료 토큰에 null을 반환한다`() {
        val expiredProvider = JwtProvider(properties.copy(accessTokenExpiration = -1_000))
        val token = expiredProvider.createAccessToken(1L, "CUSTOMER")

        assertThat(sut.parseToken(token)).isNull()
    }

    // --- parseRefreshToken: REFRESH 토큰만 수용, sid/jti 복원 ---

    @Test
    fun `parseRefreshToken은 REFRESH 토큰의 userId sessionId jti를 복원한다`() {
        val refresh = sut.createRefreshToken(99L, "sess-abc", "jti-xyz")

        val claims = sut.parseRefreshToken(refresh)

        assertThat(claims).isNotNull
        assertThat(claims!!.userId).isEqualTo(99L)
        assertThat(claims.sessionId).isEqualTo("sess-abc")
        assertThat(claims.jti).isEqualTo("jti-xyz")
    }

    @Test
    fun `parseRefreshToken은 회전돼도 같은 sessionId를 유지한다`() {
        val first = sut.createRefreshToken(99L, "sess-fixed", "jti-1")
        val rotated = sut.createRefreshToken(99L, "sess-fixed", "jti-2")

        assertThat(sut.parseRefreshToken(first)!!.sessionId).isEqualTo("sess-fixed")
        assertThat(sut.parseRefreshToken(rotated)!!.sessionId).isEqualTo("sess-fixed")
        assertThat(sut.parseRefreshToken(first)!!.jti).isNotEqualTo(sut.parseRefreshToken(rotated)!!.jti)
    }

    @Test
    fun `parseRefreshToken은 ACCESS 토큰을 거부한다`() {
        val access = sut.createAccessToken(99L, "CUSTOMER")

        assertThat(sut.parseRefreshToken(access)).isNull()
    }

    @Test
    fun `parseRefreshToken은 위조 토큰에 null을 반환한다`() {
        assertThat(sut.parseRefreshToken("bad")).isNull()
    }

    // --- createSignupToken / parseSignupToken: SIGNUP 토큰만 수용 ---

    @Test
    fun `SIGNUP 토큰을 파싱하면 provider와 oauthId, prefill을 복원한다`() {
        val token = sut.createSignupToken("KAKAO", "kakao-123", "a@b.com", "닉네임")

        val claims = sut.parseSignupToken(token)

        assertThat(claims).isNotNull
        assertThat(claims!!.provider).isEqualTo("KAKAO")
        assertThat(claims.oauthId).isEqualTo("kakao-123")
        assertThat(claims.email).isEqualTo("a@b.com")
        assertThat(claims.nickname).isEqualTo("닉네임")
    }

    @Test
    fun `SIGNUP 토큰은 prefill이 없으면 null로 복원한다`() {
        val token = sut.createSignupToken("KAKAO", "kakao-123", null, null)

        val claims = sut.parseSignupToken(token)

        assertThat(claims).isNotNull
        assertThat(claims!!.email).isNull()
        assertThat(claims.nickname).isNull()
    }

    @Test
    fun `parseSignupToken은 ACCESS 토큰을 거부한다`() {
        val access = sut.createAccessToken(1L, "CUSTOMER")

        assertThat(sut.parseSignupToken(access)).isNull()
    }
}
