package com.carry.user.domain.model

import com.carry.user.domain.vo.Email
import com.carry.user.domain.vo.OAuthInfo
import com.carry.user.domain.vo.OAuthProvider
import com.carry.user.domain.vo.Phone
import com.carry.user.domain.vo.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class UserTest {

    private fun createUser(
        email: String = "test@example.com",
        name: String = "홍길동",
        phone: String = "01012345678",
        role: UserRole = UserRole.CUSTOMER,
    ): User = User.create(
        email = Email(email),
        name = name,
        phone = Phone(phone),
        role = role,
        oauthInfo = OAuthInfo(OAuthProvider.KAKAO, "kakao-123"),
    )

    private fun reconstitutedUser(
        isActive: Boolean = true,
    ): User = User.reconstitute(
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
    inner class Create {

        @Test
        fun `새 사용자를 생성한다`() {
            val user = createUser()

            assertThat(user.id).isNull()
            assertThat(user.email).isEqualTo(Email("test@example.com"))
            assertThat(user.name).isEqualTo("홍길동")
            assertThat(user.phone).isEqualTo(Phone("01012345678"))
            assertThat(user.role).isEqualTo(UserRole.CUSTOMER)
            assertThat(user.isActive).isTrue()
            assertThat(user.createdAt).isNull()
        }

        @Test
        fun `빈 이름으로 생성하면 실패한다`() {
            assertThatThrownBy { createUser(name = "") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("이름")
        }

        @Test
        fun `공백 이름으로 생성하면 실패한다`() {
            assertThatThrownBy { createUser(name = "   ") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("이름")
        }

        @Test
        fun `기본 역할은 CUSTOMER이다`() {
            val user = User.create(
                email = Email("test@example.com"),
                name = "홍길동",
                phone = Phone("01012345678"),
                oauthInfo = OAuthInfo(OAuthProvider.KAKAO, "kakao-123"),
            )
            assertThat(user.role).isEqualTo(UserRole.CUSTOMER)
        }

        @Test
        fun `CARRIER 역할로 생성할 수 있다`() {
            val user = createUser(role = UserRole.CARRIER)
            assertThat(user.role).isEqualTo(UserRole.CARRIER)
        }
    }

    @Nested
    inner class Reconstitute {

        @Test
        fun `영속성에서 복원된 사용자는 ID와 타임스탬프를 가진다`() {
            val user = reconstitutedUser()

            assertThat(user.id).isEqualTo(1L)
            assertThat(user.createdAt).isNotNull()
            assertThat(user.updatedAt).isNotNull()
        }
    }

    @Nested
    inner class UpdateProfile {

        @Test
        fun `프로필을 수정한다`() {
            val user = reconstitutedUser()

            user.updateProfile("김철수", Phone("01098765432"))

            assertThat(user.name).isEqualTo("김철수")
            assertThat(user.phone).isEqualTo(Phone("01098765432"))
        }

        @Test
        fun `비활성 계정의 프로필은 수정할 수 없다`() {
            val user = reconstitutedUser(isActive = false)

            assertThatThrownBy { user.updateProfile("김철수", Phone("01098765432")) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("비활성 계정")
        }

        @Test
        fun `빈 이름으로 수정하면 실패한다`() {
            val user = reconstitutedUser()

            assertThatThrownBy { user.updateProfile("", Phone("01098765432")) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("이름")
        }
    }

    @Nested
    inner class Deactivate {

        @Test
        fun `계정을 비활성화한다`() {
            val user = reconstitutedUser()

            user.deactivate()

            assertThat(user.isActive).isFalse()
        }

        @Test
        fun `이미 비활성인 계정을 비활성화하면 실패한다`() {
            val user = reconstitutedUser(isActive = false)

            assertThatThrownBy { user.deactivate() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("이미 비활성")
        }
    }
}
