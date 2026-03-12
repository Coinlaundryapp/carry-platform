package com.carry.user.application.service

import com.carry.user.application.port.outbound.UserPersistencePort
import com.carry.user.domain.exception.UserNotFoundException
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

class UserCommandServiceTest {

    private val userPersistencePort = mockk<UserPersistencePort>()
    private val sut = UserCommandService(userPersistencePort)

    private fun aUser(isActive: Boolean = true) = User.reconstitute(
        id = 1L,
        email = Email("test@example.com"),
        name = "홍길동",
        phone = Phone("01012345678"),
        role = UserRole.CUSTOMER,
        oauthInfo = OAuthInfo(OAuthProvider.KAKAO, "kakao-123"),
        isActive = isActive,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Nested
    inner class UpdateProfile {

        @Test
        fun `프로필을 수정한다`() {
            val user = aUser()
            every { userPersistencePort.findById(1L) } returns user
            val savedUser = slot<User>()
            every { userPersistencePort.save(capture(savedUser)) } answers { savedUser.captured }

            val result = sut.updateProfile(1L, "김철수", "01098765432")

            assertThat(result.name).isEqualTo("김철수")
            assertThat(result.phone).isEqualTo(Phone("01098765432"))
        }

        @Test
        fun `존재하지 않는 사용자의 프로필을 수정하면 예외가 발생한다`() {
            every { userPersistencePort.findById(999L) } returns null

            assertThatThrownBy { sut.updateProfile(999L, "김철수", "01098765432") }
                .isInstanceOf(UserNotFoundException::class.java)
        }

        @Test
        fun `비활성 계정의 프로필을 수정하면 예외가 발생한다`() {
            val user = aUser(isActive = false)
            every { userPersistencePort.findById(1L) } returns user

            assertThatThrownBy { sut.updateProfile(1L, "김철수", "01098765432") }
                .isInstanceOf(IllegalStateException::class.java)
        }
    }

    @Nested
    inner class Deactivate {

        @Test
        fun `계정을 비활성화한다`() {
            val user = aUser()
            every { userPersistencePort.findById(1L) } returns user
            every { userPersistencePort.save(any()) } answers { firstArg() }

            sut.deactivate(1L)

            verify { userPersistencePort.save(match { !it.isActive }) }
        }

        @Test
        fun `존재하지 않는 사용자를 비활성화하면 예외가 발생한다`() {
            every { userPersistencePort.findById(999L) } returns null

            assertThatThrownBy { sut.deactivate(999L) }
                .isInstanceOf(UserNotFoundException::class.java)
        }
    }
}
