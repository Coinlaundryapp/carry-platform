package com.carry.user.application.service

import com.carry.user.application.port.inbound.LoginResult
import com.carry.user.application.port.outbound.AuthTokenPort
import com.carry.user.application.port.outbound.IssuedRefreshToken
import com.carry.user.application.port.outbound.OAuthProfile
import com.carry.user.application.port.outbound.OAuthProfileClient
import com.carry.user.application.port.outbound.OAuthProfileResolver
import com.carry.user.application.port.outbound.RefreshTokenClaims
import com.carry.user.application.port.outbound.RefreshTokenStorePort
import com.carry.user.application.port.outbound.RotateResult
import com.carry.user.application.port.outbound.SignupIdentity
import com.carry.user.application.port.outbound.UserPersistencePort
import com.carry.user.domain.exception.AuthTokenInvalidException
import com.carry.user.domain.exception.EmailAlreadyExistsException
import com.carry.user.domain.exception.InactiveUserException
import com.carry.user.domain.exception.RefreshTokenReuseException
import com.carry.user.domain.model.User
import com.carry.user.domain.vo.Email
import com.carry.user.domain.vo.OAuthInfo
import com.carry.user.domain.vo.OAuthProvider
import com.carry.user.domain.vo.Phone
import com.carry.user.domain.vo.UserRole
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
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
    private val oAuthProfileClientResolver = mockk<OAuthProfileResolver>()
    private val authTokenPort = mockk<AuthTokenPort>()
    private val refreshTokenStorePort = mockk<RefreshTokenStorePort>(relaxUnitFun = true)
    private val sut = AuthService(userPersistencePort, oAuthProfileClientResolver, authTokenPort, refreshTokenStorePort)

    private fun user(
        id: Long = 1L,
        oauthId: String = "kakao-123",
        role: UserRole = UserRole.CUSTOMER,
        active: Boolean = true,
        emailVerified: Boolean = false,
        email: String = "u@example.com",
    ) = User.reconstitute(
        id = id,
        email = Email(email),
        emailVerified = emailVerified,
        name = "유저",
        phone = Phone("01012345678"),
        role = role,
        isActive = active,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Nested
    inner class DevLogin {

        private fun stubTokenIssuance(userId: Long) {
            every { authTokenPort.issueRefreshToken(userId) } returns
                IssuedRefreshToken(token = "refresh", sessionId = "sess", jti = "jti")
            every { authTokenPort.issueAccessToken(userId, any()) } returns "access"
        }

        @Test
        fun `신규 역할이면 DEV 신원으로 사용자를 생성하고 그 역할로 토큰을 발급한다`() {
            val devInfo = OAuthInfo(OAuthProvider.DEV, "dev:carrier")
            every { userPersistencePort.findByOAuthInfo(devInfo) } returns null
            val saved = slot<User>()
            every { userPersistencePort.save(capture(saved)) } answers {
                User.reconstitute(
                    id = 7L, email = saved.captured.email, emailVerified = saved.captured.emailVerified,
                    name = saved.captured.name, phone = saved.captured.phone, role = saved.captured.role,
                    isActive = true, createdAt = Instant.now(), updatedAt = Instant.now(),
                )
            }
            every { userPersistencePort.linkOAuthAccount(7L, devInfo) } just Runs
            stubTokenIssuance(7L)

            val tokens = sut.devLogin(UserRole.CARRIER)

            // 생성된 사용자는 요청 역할을 갖는다 — loginOrRegister가 못 하던 핵심 속성
            assertThat(saved.captured.role).isEqualTo(UserRole.CARRIER)
            assertThat(tokens.accessToken).isEqualTo("access")
            // access 토큰은 그 역할로 발급된다
            verify(exactly = 1) { authTokenPort.issueAccessToken(7L, UserRole.CARRIER) }
            verify(exactly = 1) { userPersistencePort.linkOAuthAccount(7L, devInfo) }
        }

        @Test
        fun `같은 역할로 다시 호출하면 기존 dev 사용자를 재사용한다 — 중복 생성 없음`() {
            val existing = User.reconstitute(
                id = 3L, email = Email("u@example.com"), emailVerified = false, name = "유저",
                phone = Phone("01012345678"), role = UserRole.COORDINATOR, isActive = true,
                createdAt = Instant.now(), updatedAt = Instant.now(),
            )
            every {
                userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.DEV, "dev:coordinator"))
            } returns existing
            stubTokenIssuance(3L)

            sut.devLogin(UserRole.COORDINATOR)

            verify(exactly = 0) { userPersistencePort.save(any()) }
            verify(exactly = 0) { userPersistencePort.linkOAuthAccount(any(), any()) }
            verify(exactly = 1) { authTokenPort.issueAccessToken(3L, UserRole.COORDINATOR) }
        }
    }

    @Nested
    inner class LoginOrRegister {

        @Test
        fun `기존 사용자가 로그인하면 기존 정보를 반환한다`() {
            val existingUser = user(id = 1L, oauthId = "kakao-123")
            every {
                userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-123"))
            } returns existingUser

            val result = sut.loginOrRegister(OAuthProvider.KAKAO, "kakao-123", "u@example.com", false, "유저", "01012345678")

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
                    id = 2L, email = saved.captured.email, emailVerified = saved.captured.emailVerified,
                    name = saved.captured.name, phone = saved.captured.phone, role = saved.captured.role,
                    isActive = true, createdAt = Instant.now(), updatedAt = Instant.now(),
                )
            }
            every { userPersistencePort.linkOAuthAccount(2L, OAuthInfo(OAuthProvider.KAKAO, "kakao-new")) } just Runs

            val result = sut.loginOrRegister(OAuthProvider.KAKAO, "kakao-new", "new@example.com", false, "신규", "01098765432")

            assertThat(result.id).isEqualTo(2L)
            verify(exactly = 1) { userPersistencePort.save(any()) }
            verify(exactly = 1) { userPersistencePort.linkOAuthAccount(2L, OAuthInfo(OAuthProvider.KAKAO, "kakao-new")) }
        }
    }

    @Nested
    inner class Login {

        private fun stubResolve(provider: OAuthProvider = OAuthProvider.KAKAO): OAuthProfileClient {
            val client = mockk<OAuthProfileClient>()
            every { oAuthProfileClientResolver.resolve(provider) } returns client
            return client
        }

        @Test
        fun `기존 신원이면 즉시 토큰`() {
            val client = stubResolve()
            every { client.fetchProfile("kakao-at") } returns
                OAuthProfile(oauthId = "kakao-123", email = "u@example.com", nickname = "유저")
            every { userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-123")) } returns
                user(id = 1L, role = UserRole.COORDINATOR)
            every { authTokenPort.issueAccessToken(1L, UserRole.COORDINATOR) } returns "acc"
            every { authTokenPort.issueRefreshToken(1L) } returns IssuedRefreshToken("ref", "sess-1", "jti-1")

            val result = sut.login(OAuthProvider.KAKAO, "kakao-at")

            assertThat(result).isInstanceOf(LoginResult.Registered::class.java)
            val tokens = (result as LoginResult.Registered).tokens
            assertThat(tokens.accessToken).isEqualTo("acc")
            assertThat(tokens.refreshToken).isEqualTo("ref")
            verify { refreshTokenStorePort.start("sess-1", "jti-1") }
        }

        @Test
        fun `미존재+양측 검증 이메일 매치면 연동 후 토큰`() {
            val client = stubResolve(OAuthProvider.NAVER)
            every { client.fetchProfile("naver-at") } returns
                OAuthProfile(oauthId = "naver-new", email = "u@example.com", nickname = "네이버닉", emailVerified = true)
            every { userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.NAVER, "naver-new")) } returns null
            val existing = user(id = 1L, role = UserRole.CUSTOMER, emailVerified = true, email = "u@example.com")
            every { userPersistencePort.findByEmail(Email("u@example.com")) } returns existing
            every { userPersistencePort.linkOAuthAccount(1L, OAuthInfo(OAuthProvider.NAVER, "naver-new")) } just Runs
            every { authTokenPort.issueAccessToken(1L, UserRole.CUSTOMER) } returns "acc"
            every { authTokenPort.issueRefreshToken(1L) } returns IssuedRefreshToken("ref", "sess-1", "jti-1")

            val result = sut.login(OAuthProvider.NAVER, "naver-at")

            assertThat(result).isInstanceOf(LoginResult.Registered::class.java)
            assertThat((result as LoginResult.Registered).tokens.accessToken).isEqualTo("acc")
            verify(exactly = 1) { userPersistencePort.linkOAuthAccount(1L, OAuthInfo(OAuthProvider.NAVER, "naver-new")) }
        }

        @Test
        fun `이메일 미검증이면 연동 안 하고 RegistrationRequired`() {
            val client = stubResolve(OAuthProvider.NAVER)
            every { client.fetchProfile("naver-at") } returns
                OAuthProfile(oauthId = "naver-new", email = "u@example.com", nickname = "네이버닉", emailVerified = false)
            every { userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.NAVER, "naver-new")) } returns null
            every {
                authTokenPort.issueSignupToken(OAuthProvider.NAVER, "naver-new", "u@example.com", "네이버닉", false)
            } returns "signup-token"

            val result = sut.login(OAuthProvider.NAVER, "naver-at")

            assertThat(result).isInstanceOf(LoginResult.RegistrationRequired::class.java)
            // findByEmail은 emailVerified=false인 프로필에서는 애초에 호출되지 않는다(unstubbed면 mockk가 실패시킨다).
            verify(exactly = 0) { userPersistencePort.linkOAuthAccount(any(), any()) }
        }

        @Test
        fun `검증돼도 기존 유저 email_verified=false면 연동 안 함`() {
            val client = stubResolve(OAuthProvider.NAVER)
            every { client.fetchProfile("naver-at") } returns
                OAuthProfile(oauthId = "naver-new", email = "u@example.com", nickname = "네이버닉", emailVerified = true)
            every { userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.NAVER, "naver-new")) } returns null
            val existing = user(id = 1L, emailVerified = false, email = "u@example.com")
            every { userPersistencePort.findByEmail(Email("u@example.com")) } returns existing
            every {
                authTokenPort.issueSignupToken(OAuthProvider.NAVER, "naver-new", "u@example.com", "네이버닉", true)
            } returns "signup-token"

            val result = sut.login(OAuthProvider.NAVER, "naver-at")

            assertThat(result).isInstanceOf(LoginResult.RegistrationRequired::class.java)
            verify(exactly = 0) { userPersistencePort.linkOAuthAccount(any(), any()) }
        }

        @Test
        fun `이메일 null이면 연동 안 함`() {
            val client = stubResolve(OAuthProvider.NAVER)
            every { client.fetchProfile("naver-at") } returns
                OAuthProfile(oauthId = "naver-new", email = null, nickname = "네이버닉", emailVerified = false)
            every { userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.NAVER, "naver-new")) } returns null
            every {
                authTokenPort.issueSignupToken(OAuthProvider.NAVER, "naver-new", null, "네이버닉", false)
            } returns "signup-token"

            val result = sut.login(OAuthProvider.NAVER, "naver-at")

            assertThat(result).isInstanceOf(LoginResult.RegistrationRequired::class.java)
            // email=null이면 findByEmail은 호출되지 않는다(unstubbed면 mockk가 실패시킨다).
        }

        @Test
        fun `비활성 기존 유저는 로그인할 수 없다`() {
            val client = stubResolve()
            every { client.fetchProfile("kakao-at") } returns
                OAuthProfile(oauthId = "kakao-123", email = null, nickname = null)
            every { userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-123")) } returns
                user(id = 1L, active = false)

            assertThatThrownBy { sut.login(OAuthProvider.KAKAO, "kakao-at") }
                .isInstanceOf(InactiveUserException::class.java)
        }
    }

    @Nested
    inner class CompleteSignup {

        @Test
        fun `가입 토큰과 폼으로 회원가입 후 토큰을 발급한다`() {
            every { authTokenPort.parseSignupToken("signup-token") } returns
                SignupIdentity(OAuthProvider.KAKAO, "kakao-new", "new@example.com", "새닉", emailVerified = false)
            every { userPersistencePort.existsByEmail(Email("new@example.com")) } returns false
            every { userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-new")) } returns null
            every { userPersistencePort.save(any()) } returns user(id = 5L, oauthId = "kakao-new")
            every { userPersistencePort.linkOAuthAccount(5L, OAuthInfo(OAuthProvider.KAKAO, "kakao-new")) } just Runs
            every { authTokenPort.issueAccessToken(5L, UserRole.CUSTOMER) } returns "acc"
            every { authTokenPort.issueRefreshToken(5L) } returns IssuedRefreshToken("ref", "sess-5", "jti-5")

            val tokens = sut.completeSignup("signup-token", "이름", "01012345678", "new@example.com")

            assertThat(tokens.accessToken).isEqualTo("acc")
            assertThat(tokens.refreshToken).isEqualTo("ref")
            verify(exactly = 1) { userPersistencePort.save(any()) }
            verify { refreshTokenStorePort.start("sess-5", "jti-5") }
        }

        @Test
        fun `무효한 가입 토큰은 거부한다`() {
            every { authTokenPort.parseSignupToken("bad") } returns null

            assertThatThrownBy { sut.completeSignup("bad", "이름", "01012345678", "a@b.com") }
                .isInstanceOf(AuthTokenInvalidException::class.java)
        }

        @Test
        fun `completeSignup 이메일 충돌이면 EmailAlreadyExists`() {
            every { authTokenPort.parseSignupToken("signup-token") } returns
                SignupIdentity(OAuthProvider.KAKAO, "kakao-new", "new@example.com", "새닉", emailVerified = false)
            every { userPersistencePort.existsByEmail(Email("new@example.com")) } returns true

            assertThatThrownBy { sut.completeSignup("signup-token", "이름", "01012345678", "new@example.com") }
                .isInstanceOf(EmailAlreadyExistsException::class.java)
            verify(exactly = 0) { userPersistencePort.save(any()) }
        }
    }

    @Nested
    inner class Refresh {

        @Test
        fun `유효한 refresh 토큰을 회전해 새 access·refresh를 발급한다`() {
            every { authTokenPort.parseRefreshToken("ref") } returns RefreshTokenClaims(1L, "sess-1", "old-jti")
            every { userPersistencePort.findById(1L) } returns user(id = 1L, role = UserRole.ADMIN)
            every { authTokenPort.issueRefreshToken(1L, "sess-1") } returns IssuedRefreshToken("new-ref", "sess-1", "new-jti")
            every { refreshTokenStorePort.rotate("sess-1", "old-jti", "new-jti") } returns RotateResult.ROTATED
            every { authTokenPort.issueAccessToken(1L, UserRole.ADMIN) } returns "new-acc"

            val tokens = sut.refresh("ref")

            assertThat(tokens.accessToken).isEqualTo("new-acc")
            assertThat(tokens.refreshToken).isEqualTo("new-ref")
            verify { refreshTokenStorePort.rotate("sess-1", "old-jti", "new-jti") }
        }

        @Test
        fun `재사용이 감지되면 세션 폐기 예외를 던진다`() {
            every { authTokenPort.parseRefreshToken("ref") } returns RefreshTokenClaims(1L, "sess-1", "old-jti")
            every { userPersistencePort.findById(1L) } returns user(id = 1L)
            every { authTokenPort.issueRefreshToken(1L, "sess-1") } returns IssuedRefreshToken("new-ref", "sess-1", "new-jti")
            every { refreshTokenStorePort.rotate("sess-1", "old-jti", "new-jti") } returns RotateResult.REUSE

            assertThatThrownBy { sut.refresh("ref") }.isInstanceOf(RefreshTokenReuseException::class.java)
            verify(exactly = 0) { authTokenPort.issueAccessToken(any(), any()) }
        }

        @Test
        fun `세션이 없으면(로그아웃·만료) 거부한다`() {
            every { authTokenPort.parseRefreshToken("ref") } returns RefreshTokenClaims(1L, "sess-1", "old-jti")
            every { userPersistencePort.findById(1L) } returns user(id = 1L)
            every { authTokenPort.issueRefreshToken(1L, "sess-1") } returns IssuedRefreshToken("new-ref", "sess-1", "new-jti")
            every { refreshTokenStorePort.rotate("sess-1", "old-jti", "new-jti") } returns RotateResult.ABSENT

            assertThatThrownBy { sut.refresh("ref") }.isInstanceOf(AuthTokenInvalidException::class.java)
        }

        @Test
        fun `무효한 refresh 토큰은 거부한다`() {
            every { authTokenPort.parseRefreshToken("bad") } returns null

            assertThatThrownBy { sut.refresh("bad") }.isInstanceOf(AuthTokenInvalidException::class.java)
        }

        @Test
        fun `refresh 대상 유저가 없으면 거부한다`() {
            every { authTokenPort.parseRefreshToken("ref") } returns RefreshTokenClaims(99L, "sess-9", "jti")
            every { userPersistencePort.findById(99L) } returns null

            assertThatThrownBy { sut.refresh("ref") }.isInstanceOf(AuthTokenInvalidException::class.java)
        }

        @Test
        fun `refresh 대상 유저가 비활성이면 거부한다`() {
            every { authTokenPort.parseRefreshToken("ref") } returns RefreshTokenClaims(1L, "sess-1", "jti")
            every { userPersistencePort.findById(1L) } returns user(id = 1L, active = false)

            assertThatThrownBy { sut.refresh("ref") }.isInstanceOf(AuthTokenInvalidException::class.java)
        }
    }

    @Nested
    inner class Logout {

        @Test
        fun `검증된 토큰의 세션을 폐기한다`() {
            every { authTokenPort.parseRefreshToken("ref") } returns RefreshTokenClaims(1L, "sess-1", "jti")

            sut.logout("ref")

            verify { refreshTokenStorePort.delete("sess-1") }
        }

        @Test
        fun `무효한 토큰은 세션을 건드리지 않고 조용히 종료한다`() {
            every { authTokenPort.parseRefreshToken("bad") } returns null

            sut.logout("bad")

            verify(exactly = 0) { refreshTokenStorePort.delete(any()) }
        }
    }
}
