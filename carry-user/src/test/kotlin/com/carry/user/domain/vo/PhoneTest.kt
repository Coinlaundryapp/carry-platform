package com.carry.user.domain.vo

import com.carry.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PhoneTest {

    @ParameterizedTest
    @ValueSource(strings = ["01012345678", "010-1234-5678", "01112345678", "016-123-4567"])
    fun `유효한 전화번호를 생성한다`(value: String) {
        val phone = Phone(value)
        assertThat(phone.value).isEqualTo(value)
    }

    @Test
    fun `빈 전화번호는 거부한다`() {
        assertThatThrownBy { Phone("") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("비어있을 수 없습니다")
    }

    @ParameterizedTest
    @ValueSource(strings = ["12345678", "020-1234-5678", "010-12-5678", "not-a-phone"])
    fun `잘못된 형식의 전화번호는 거부한다`(value: String) {
        assertThatThrownBy { Phone(value) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("올바르지 않은 전화번호 형식")
    }
}
