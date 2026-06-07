package com.carry.laundromat.domain.vo

import com.carry.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LaundromatAddressTest {

    @Test
    fun `유효한 주소를 생성한다`() {
        val address = LaundromatAddress("서울시 강남구 테헤란로 123", "1층", "06234")
        assertThat(address.roadAddress).isEqualTo("서울시 강남구 테헤란로 123")
    }

    @Test
    fun `상세주소와 우편번호는 선택사항이다`() {
        val address = LaundromatAddress("서울시 강남구 테헤란로 123")
        assertThat(address.detailAddress).isNull()
        assertThat(address.zipCode).isNull()
    }

    @Test
    fun `빈 도로명 주소는 거부한다`() {
        assertThatThrownBy { LaundromatAddress("") }
            .isInstanceOf(BusinessException::class.java)
    }
}
