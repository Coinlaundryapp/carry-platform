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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class UserQueryServiceTest {

    private val userPersistencePort = mockk<UserPersistencePort>()
    private val sut = UserQueryService(userPersistencePort)

    private fun aUser(id: Long = 1L) = User.reconstitute(
        id = id,
        email = Email("test@example.com"),
        name = "홍길동",
        phone = Phone("01012345678"),
        role = UserRole.CUSTOMER,
        oauthInfo = OAuthInfo(OAuthProvider.KAKAO, "kakao-123"),
        isActive = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun `사용자 프로필을 조회한다`() {
        val user = aUser()
        every { userPersistencePort.findById(1L) } returns user

        val result = sut.getProfile(1L)

        assertThat(result.name).isEqualTo("홍길동")
        assertThat(result.email).isEqualTo(Email("test@example.com"))
    }

    @Test
    fun `존재하지 않는 사용자를 조회하면 예외가 발생한다`() {
        every { userPersistencePort.findById(999L) } returns null

        assertThatThrownBy { sut.getProfile(999L) }
            .isInstanceOf(UserNotFoundException::class.java)
    }
}
