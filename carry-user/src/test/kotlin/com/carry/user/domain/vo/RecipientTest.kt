package com.carry.user.domain.vo

import com.carry.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RecipientTest {
    @Test
    fun `이름과 전화번호로 수령인을 생성한다`() {
        val r = Recipient("홍길동", "01012345678")
        assertThat(r.name).isEqualTo("홍길동")
        assertThat(r.phone).isEqualTo("01012345678")
    }

    @Test
    fun `빈 이름은 거부한다`() {
        assertThatThrownBy { Recipient(" ", "01012345678") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("수령인 이름")
    }

    @Test
    fun `빈 전화번호는 거부한다`() {
        assertThatThrownBy { Recipient("홍길동", "") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("수령인 전화번호")
    }
}
