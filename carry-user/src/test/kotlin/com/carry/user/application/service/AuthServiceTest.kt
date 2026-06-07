package com.carry.user.application.service

import com.carry.user.application.port.inbound.LoginResult
import com.carry.user.application.port.outbound.AuthTokenPort
import com.carry.user.application.port.outbound.OAuthProfile
import com.carry.user.application.port.outbound.OAuthProfileClient
import com.carry.user.application.port.outbound.SignupIdentity
import com.carry.user.application.port.outbound.UserPersistencePort
import com.carry.user.domain.exception.AuthTokenInvalidException
import com.carry.user.domain.exception.InactiveUserException
import com.carry.user.domain.model.User
import com.carry.user.domain.vo.Email
import com.carry.user.domain.vo.OAuthInfo
import com.carry.user.domain.vo.OAuthProvider
import com.carry.user.domain.vo.Phone
import com.carry.user.domain.vo.UserRole
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class AuthServiceTest {

    private val userPersistencePort = mockk<UserPersistencePort>()
    private val oAuthProfileClient = mockk<OAuthProfileClient>()
    private val authTokenPort = mockk<AuthTokenPort>()
    private val sut = AuthService(userPersistencePort, oAuthProfileClient, authTokenPort)

    private fun user(
        id: Long = 1L,
        oauthId: String = "kakao-123",
        role: UserRole = UserRole.CUSTOMER,
        active: Boolean = true,
    ) = User.reconstitute(
        id = id,
        email = Email("u@example.com"),
        name = "유저",
        phone = Phone("01012345678"),
        role = role,
        oauthInfo = OAuthInfo(OAuthProvider.KAKAO, oauthId),
        isActive = active,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Nested
    inner class LoginOrRegister {

        @Test
        fun `기존 사용자가 로그인하면 기존 정보를 반환한다`() {
            val existingUser = user(id = 1L, oauthId = "kakao-123")
            every {
                userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-123"))
            } returns existingUser

            val result = sut.loginOrRegister(OAuthProvider.KAKAO, "kakao-123", "u@example.com", "유저", "01012345678")

            assertThat(result.id).isEqualTo(1L)
            verify(exactly = 0) { userPersistencePort.save(any()) }
        }

        @Test
        fun `신규 사용자가 로그인하면 회원가입 후 반환한다`() {
            every {
                userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-new"))
            } returns null
            val saved = slot<User>()
            every { userPersistencePort.save(capture(saved)) } answers {
                User.reconstitute(
                    id = 2L, email = saved.captured.email, name = saved.captured.name,
                    phone = saved.captured.phone, role = saved.captured.role, oauthInfo = saved.captured.oauthInfo,
                    isActive = true, createdAt = Instant.now(), updatedAt = Instant.now(),
                )
            }

            val result = sut.loginOrRegister(OAuthProvider.KAKAO, "kakao-new", "new@example.com", "신규", "01098765432")

            assertThat(result.id).isEqualTo(2L)
            verify(exactly = 1) { userPersistencePort.save(any()) }
        }
    }

    @Nested
    inner class LoginWithKakao {

        @Test
        fun `기존 유저는 Kakao 로그인 시 토큰을 즉시 발급한다`() {
            every { oAuthProfileClient.fetchKakaoProfile("kakao-at") } returns
                OAuthProfile(oauthId = "kakao-123", email = "u@example.com", nickname = "유저")
            every { userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-123")) } returns
                user(id = 1L, role = UserRole.COORDINATOR)
            every { authTokenPort.issueAccessToken(1L, UserRole.COORDINATOR) } returns "acc"
            every { authTokenPort.issueRefreshToken(1L) } returns "ref"

            val result = sut.loginWithKakao("kakao-at")

            assertThat(result).isInstanceOf(LoginResult.Registered::class.java)
            val tokens = (result as LoginResult.Registered).tokens
            assertThat(tokens.accessToken).isEqualTo("acc")
            assertThat(tokens.refreshToken).isEqualTo("ref")
        }

        @Test
        fun `신규 유저는 Kakao 로그인 시 가입 토큰과 prefill을 반환한다`() {
            every { oAuthProfileClient.fetchKakaoProfile("kakao-at") } returns
                OAuthProfile(oauthId = "kakao-new", email = "new@example.com", nickname = "새닉")
            every { userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-new")) } returns null
            every {
                authTokenPort.issueSignupToken(OAuthProvider.KAKAO, "kakao-new", "new@example.com", "새닉")
            } returns "signup-token"

            val result = sut.loginWithKakao("kakao-at")

            assertThat(result).isInstanceOf(LoginResult.RegistrationRequired::class.java)
            val rr = result as LoginResult.RegistrationRequired
            assertThat(rr.signupToken).isEqualTo("signup-token")
            assertThat(rr.prefill.email).isEqualTo("new@example.com")
            assertThat(rr.prefill.nickname).isEqualTo("새닉")
        }

        @Test
        fun `비활성 기존 유저는 로그인할 수 없다`() {
            every { oAuthProfileClient.fetchKakaoProfile("kakao-at") } returns
                OAuthProfile(oauthId = "kakao-123", email = null, nickname = null)
            every { userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-123")) } returns
                user(id = 1L, active = false)

            assertThatThrownBy { sut.loginWithKakao("kakao-at") }
                .isInstanceOf(InactiveUserException::class.java)
        }
    }

    @Nested
    inner class CompleteSignup {

        @Test
        fun `가입 토큰과 폼으로 회원가입 후 토큰을 발급한다`() {
            every { authTokenPort.parseSignupToken("signup-token") } returns
                SignupIdentity(OAuthProvider.KAKAO, "kakao-new", "new@example.com", "새닉")
            every { userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-new")) } returns null
            every { userPersistencePort.save(any()) } returns user(id = 5L, oauthId = "kakao-new")
            every { authTokenPort.issueAccessToken(5L, UserRole.CUSTOMER) } returns "acc"
            every { authTokenPort.issueRefreshToken(5L) } returns "ref"

            val tokens = sut.completeSignup("signup-token", "이름", "01012345678", "new@example.com")

            assertThat(tokens.accessToken).isEqualTo("acc")
            assertThat(tokens.refreshToken).isEqualTo("ref")
            verify(exactly = 1) { userPersistencePort.save(any()) }
        }

        @Test
        fun `무효한 가입 토큰은 거부한다`() {
            every { authTokenPort.parseSignupToken("bad") } returns null

            assertThatThrownBy { sut.completeSignup("bad", "이름", "01012345678", "a@b.com") }
                .isInstanceOf(AuthTokenInvalidException::class.java)
        }
    }

    @Nested
    inner class Refresh {

        @Test
        fun `유효한 refresh 토큰으로 새 access 토큰을 발급한다`() {
            every { authTokenPort.parseRefreshToken("ref") } returns 1L
            every { userPersistencePort.findById(1L) } returns user(id = 1L, role = UserRole.ADMIN)
            every { authTokenPort.issueAccessToken(1L, UserRole.ADMIN) } returns "new-acc"

            val accessToken = sut.refresh("ref")

            assertThat(accessToken).isEqualTo("new-acc")
        }

        @Test
        fun `무효한 refresh 토큰은 거부한다`() {
            every { authTokenPort.parseRefreshToken("bad") } returns null

            assertThatThrownBy { sut.refresh("bad") }.isInstanceOf(AuthTokenInvalidException::class.java)
        }

        @Test
        fun `refresh 대상 유저가 없으면 거부한다`() {
            every { authTokenPort.parseRefreshToken("ref") } returns 99L
            every { userPersistencePort.findById(99L) } returns null

            assertThatThrownBy { sut.refresh("ref") }.isInstanceOf(AuthTokenInvalidException::class.java)
        }

        @Test
        fun `refresh 대상 유저가 비활성이면 거부한다`() {
            every { authTokenPort.parseRefreshToken("ref") } returns 1L
            every { userPersistencePort.findById(1L) } returns user(id = 1L, active = false)

            assertThatThrownBy { sut.refresh("ref") }.isInstanceOf(AuthTokenInvalidException::class.java)
        }
    }
}
