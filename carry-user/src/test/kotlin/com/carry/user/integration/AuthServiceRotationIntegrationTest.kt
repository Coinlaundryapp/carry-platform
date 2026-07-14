package com.carry.user.integration

import com.carry.security.jwt.JwtProperties
import com.carry.user.application.service.AuthService
import com.carry.security.jwt.JwtProvider
import com.carry.user.adapter.outbound.auth.InMemoryRefreshTokenStore
import com.carry.user.adapter.outbound.auth.JwtAuthTokenAdapter
import com.carry.user.adapter.outbound.auth.OAuthProfileClientResolver
import com.carry.user.application.port.outbound.OAuthProfile
import com.carry.user.application.port.outbound.OAuthProfileClient
import com.carry.user.application.port.outbound.OAuthProfileResolver
import com.carry.user.application.port.outbound.UserPersistencePort
import com.carry.user.domain.exception.AuthTokenInvalidException
import com.carry.user.domain.exception.RefreshTokenReuseException
import com.carry.user.domain.model.User
import com.carry.user.domain.vo.Email
import com.carry.user.domain.vo.OAuthInfo
import com.carry.user.domain.vo.OAuthProvider
import com.carry.user.domain.vo.Phone
import com.carry.user.domain.vo.UserRole
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 실 [JwtAuthTokenAdapter] + [InMemoryRefreshTokenStore]를 묶어 토큰 plumbing(실 jti 흐름)을
 * 검증하는 통합 테스트. 모킹된 [AuthServiceTest]가 못 잡는 "발급된 토큰의 jti가 그대로 회전에
 * 쓰이는가"를 실제 토큰 발급/파싱/회전으로 확인한다.
 *
 * 재사용 감지는 **2회 회전**으로 옛 토큰을 cur도 prev도 아니게 만들어 timing 의존 없이 결정적으로 발화시킨다.
 */
class AuthServiceRotationIntegrationTest {

    private val jwtProvider = JwtProvider(JwtProperties(secret = "test-secret-test-secret-test-secret-0123456789"))
    private val authTokenPort = JwtAuthTokenAdapter(jwtProvider)
    private val store = InMemoryRefreshTokenStore(graceMillis = 10_000)
    private val userPersistencePort = mockk<UserPersistencePort>()
    private val oAuthProfileClient = mockk<OAuthProfileClient>().also {
        // resolve()가 생성 시점에 supports()를 1회 호출해 매핑을 만들므로, sut 생성 전에 스텁해야 한다.
        every { it.supports() } returns OAuthProvider.KAKAO
    }
    private val oAuthProfileClientResolver: OAuthProfileResolver = OAuthProfileClientResolver(listOf(oAuthProfileClient))
    private val sut = AuthService(userPersistencePort, oAuthProfileClientResolver, authTokenPort, store)

    private val user = User.reconstitute(
        id = 1L,
        email = Email("u@example.com"),
        emailVerified = false,
        name = "유저",
        phone = Phone("01012345678"),
        role = UserRole.CUSTOMER,
        isActive = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun login(): String {
        every { oAuthProfileClient.fetchProfile("kakao-at") } returns
            OAuthProfile(oauthId = "kakao-1", email = "u@example.com", nickname = "유저")
        every { userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-1")) } returns user
        every { userPersistencePort.findById(1L) } returns user
        return ((sut.login(OAuthProvider.KAKAO, "kakao-at")) as com.carry.user.application.port.inbound.LoginResult.Registered)
            .tokens.refreshToken
    }

    @Test
    fun `로그인 후 refresh는 회전된 새 토큰 쌍을 발급한다`() {
        val refresh0 = login()

        val rotated = sut.refresh(refresh0)

        assertThat(rotated.accessToken).isNotBlank()
        assertThat(rotated.refreshToken).isNotEqualTo(refresh0)
    }

    @Test
    fun `2회 회전 후 최초 토큰 재사용은 세션을 폐기한다`() {
        val refresh0 = login()
        val refresh1 = sut.refresh(refresh0).refreshToken // cur=jti1, prev=jti0
        val refresh2 = sut.refresh(refresh1).refreshToken // cur=jti2, prev=jti1 (jti0은 이제 옛 토큰)

        // 최초 토큰(jti0) 재사용 → REUSE
        assertThatThrownBy { sut.refresh(refresh0) }.isInstanceOf(RefreshTokenReuseException::class.java)
        // 세션 폐기 확인 — 직전까지 유효하던 refresh2도 무효
        assertThatThrownBy { sut.refresh(refresh2) }.isInstanceOf(AuthTokenInvalidException::class.java)
    }

    @Test
    fun `logout 후 refresh는 거부된다`() {
        val refresh0 = login()

        sut.logout(refresh0)

        assertThatThrownBy { sut.refresh(refresh0) }.isInstanceOf(AuthTokenInvalidException::class.java)
    }
}
