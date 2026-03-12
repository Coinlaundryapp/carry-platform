package com.carry.user.application.service

import com.carry.user.application.port.outbound.UserPersistencePort
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
import org.junit.jupiter.api.Test
import java.time.Instant

class AuthServiceTest {

    private val userPersistencePort = mockk<UserPersistencePort>()
    private val sut = AuthService(userPersistencePort)

    @Test
    fun `기존 사용자가 로그인하면 기존 정보를 반환한다`() {
        val existingUser = User.reconstitute(
            id = 1L,
            email = Email("existing@example.com"),
            name = "기존유저",
            phone = Phone("01012345678"),
            role = UserRole.CUSTOMER,
            oauthInfo = OAuthInfo(OAuthProvider.KAKAO, "kakao-123"),
            isActive = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        every {
            userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-123"))
        } returns existingUser

        val result = sut.loginOrRegister(
            provider = OAuthProvider.KAKAO,
            oauthId = "kakao-123",
            email = "existing@example.com",
            name = "기존유저",
            phone = "01012345678",
        )

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.name).isEqualTo("기존유저")
        verify(exactly = 0) { userPersistencePort.save(any()) }
    }

    @Test
    fun `신규 사용자가 로그인하면 회원가입 후 반환한다`() {
        every {
            userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, "kakao-new"))
        } returns null

        val savedUser = slot<User>()
        every { userPersistencePort.save(capture(savedUser)) } answers {
            User.reconstitute(
                id = 2L,
                email = savedUser.captured.email,
                name = savedUser.captured.name,
                phone = savedUser.captured.phone,
                role = savedUser.captured.role,
                oauthInfo = savedUser.captured.oauthInfo,
                isActive = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        }

        val result = sut.loginOrRegister(
            provider = OAuthProvider.KAKAO,
            oauthId = "kakao-new",
            email = "new@example.com",
            name = "신규유저",
            phone = "01098765432",
        )

        assertThat(result.id).isEqualTo(2L)
        assertThat(result.name).isEqualTo("신규유저")
        assertThat(result.email).isEqualTo(Email("new@example.com"))
        verify(exactly = 1) { userPersistencePort.save(any()) }
    }
}
