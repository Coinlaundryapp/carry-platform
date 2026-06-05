package com.carry.user.domain.vo

import com.carry.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EmailTest {

    @Test
    fun `유효한 이메일을 생성한다`() {
        val email = Email("user@example.com")
        assertThat(email.value).isEqualTo("user@example.com")
    }

    @ParameterizedTest
    @ValueSource(strings = ["test@gmail.com", "user.name+tag@domain.co.kr", "admin@carry.com"])
    fun `다양한 유효 이메일 형식을 허용한다`(value: String) {
        val email = Email(value)
        assertThat(email.value).isEqualTo(value)
    }

    @Test
    fun `빈 이메일은 거부한다`() {
        assertThatThrownBy { Email("") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("비어있을 수 없습니다")
    }

    @Test
    fun `공백만 있는 이메일은 거부한다`() {
        assertThatThrownBy { Email("   ") }
            .isInstanceOf(BusinessException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["invalid", "no-at-sign", "@no-local.com", "no-domain@"])
    fun `잘못된 형식의 이메일은 거부한다`(value: String) {
        assertThatThrownBy { Email(value) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("올바르지 않은 이메일 형식")
    }

    @Test
    fun `동일한 값의 Email은 동등하다`() {
        assertThat(Email("a@b.com")).isEqualTo(Email("a@b.com"))
    }
}
