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
    fun `refresh 토큰은 sessionId·jti를 담아 발급되고 parseRefreshToken으로 복원된다`() {
        val issued = sut.issueRefreshToken(11L)

        val claims = sut.parseRefreshToken(issued.token)
        assertThat(claims).isNotNull
        assertThat(claims!!.userId).isEqualTo(11L)
        assertThat(claims.sessionId).isEqualTo(issued.sessionId)
        assertThat(claims.jti).isEqualTo(issued.jti)
    }

    @Test
    fun `회전은 sessionId를 유지하고 jti만 새로 발급한다`() {
        val first = sut.issueRefreshToken(11L)
        val rotated = sut.issueRefreshToken(11L, first.sessionId)

        assertThat(rotated.sessionId).isEqualTo(first.sessionId)
        assertThat(rotated.jti).isNotEqualTo(first.jti)
    }

    @Test
    fun `새 세션 발급은 매번 다른 sessionId를 만든다`() {
        assertThat(sut.issueRefreshToken(11L).sessionId).isNotEqualTo(sut.issueRefreshToken(11L).sessionId)
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

        assertThat(sut.parseSignupToken(refresh.token)).isNull()
    }
}
