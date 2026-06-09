package com.carry.security.jwt

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class JwtSecretValidatorTest {

    private fun validatorWith(secret: String) = JwtSecretValidator(JwtProperties(secret = secret))

    @Test
    fun `비어있거나 32바이트 미만 secret 은 기동을 실패시킨다`() {
        assertThatThrownBy { validatorWith("").afterPropertiesSet() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("jwt.secret")

        assertThatThrownBy { validatorWith("short-secret-31-bytes-xxxxxxxxx").afterPropertiesSet() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `32바이트 이상 secret 은 통과한다`() {
        val secret = "a".repeat(JwtSecretValidator.MIN_SECRET_BYTES)
        assertThat(secret.toByteArray().size).isEqualTo(32)

        assertThatCode { validatorWith(secret).afterPropertiesSet() }.doesNotThrowAnyException()
    }
}
