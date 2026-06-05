package com.carry.user.domain.vo

import com.carry.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AddressTest {

    @Test
    fun `유효한 주소를 생성한다`() {
        val address = Address("서울시 강남구 테헤란로 123", "4층 401호", "06234")
        assertThat(address.roadAddress).isEqualTo("서울시 강남구 테헤란로 123")
        assertThat(address.detailAddress).isEqualTo("4층 401호")
        assertThat(address.zipCode).isEqualTo("06234")
    }

    @Test
    fun `빈 도로명 주소는 거부한다`() {
        assertThatThrownBy { Address("", "상세주소", "12345") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("도로명 주소")
    }

    @Test
    fun `빈 우편번호는 거부한다`() {
        assertThatThrownBy { Address("도로명주소", "상세주소", "") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("우편번호")
    }

    @Test
    fun `동일한 값의 Address는 동등하다`() {
        val a = Address("도로명", "상세", "12345")
        val b = Address("도로명", "상세", "12345")
        assertThat(a).isEqualTo(b)
    }
}
