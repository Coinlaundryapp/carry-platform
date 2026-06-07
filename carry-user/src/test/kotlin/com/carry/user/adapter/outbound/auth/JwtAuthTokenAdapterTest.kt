package com.carry.user.adapter.outbound.auth

import com.carry.security.jwt.JwtProperties
import com.carry.security.jwt.JwtProvider
import com.carry.user.domain.vo.OAuthProvider
import com.carry.user.domain.vo.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtAuthTokenAdapterTest {

    private val jwtProvider = JwtProvider(
        JwtProperties(secret = "test-secret-test-secret-test-secret-0123456789"),
    )
    private val sut = JwtAuthTokenAdapter(jwtProvider)

    @Test
    fun `access 토큰은 role 이름을 담아 발급되고 parseToken으로 복원된다`() {
        val token = sut.issueAccessToken(7L, UserRole.COORDINATOR)

        val principal = jwtProvider.parseToken(token)
        assertThat(principal).isNotNull
        assertThat(principal!!.userId).isEqualTo(7L)
        assertThat(principal.role).isEqualTo("COORDINATOR")
    }

    @Test
    fun `refresh 토큰은 parseRefreshToken으로 userId를 복원한다`() {
        val token = sut.issueRefreshToken(11L)

        assertThat(sut.parseRefreshToken(token)).isEqualTo(11L)
    }

    @Test
    fun `refresh 파싱은 access 토큰을 거부한다`() {
        val access = sut.issueAccessToken(1L, UserRole.CUSTOMER)

        assertThat(sut.parseRefreshToken(access)).isNull()
    }

    @Test
    fun `signup 토큰은 provider 문자열을 enum으로 복원한다`() {
        val token = sut.issueSignupToken(OAuthProvider.KAKAO, "kakao-9", "e@x.com", "닉")

        val identity = sut.parseSignupToken(token)
        assertThat(identity).isNotNull
        assertThat(identity!!.provider).isEqualTo(OAuthProvider.KAKAO)
        assertThat(identity.oauthId).isEqualTo("kakao-9")
        assertThat(identity.email).isEqualTo("e@x.com")
        assertThat(identity.nickname).isEqualTo("닉")
    }

    @Test
    fun `signup 파싱은 refresh 토큰을 거부한다`() {
        val refresh = sut.issueRefreshToken(1L)

        assertThat(sut.parseSignupToken(refresh)).isNull()
    }
}
