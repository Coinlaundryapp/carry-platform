package com.carry.order.domain.vo

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * 배송지 VO 의 필수값 검증 — 빈 문자열·공백만 있는 값이 주문에 실려 들어가면 수거·배송 단계에서
 * 사람이 손으로 메워야 한다. 생성 시점에 막는 것이 유일한 방어선이라 여기서 고정한다.
 *
 * 검증 자체는 처음부터 있었으나 테스트가 없었다(불변식 카탈로그 §6.3-8).
 */
class OrderShippingAddressTest {

    private fun address(
        roadAddress: String = "서울시 강남구 테헤란로 1",
        recipientName: String = "홍길동",
        recipientPhone: String = "010-1234-5678",
        areaCode: String = "GANGNAM",
    ) = OrderShippingAddress(
        roadAddress = roadAddress,
        detailAddress = "101동 1001호",
        zipCode = "06234",
        latitude = 37.5,
        longitude = 127.0,
        recipientName = recipientName,
        recipientPhone = recipientPhone,
        entranceInfo = null,
        areaCode = areaCode,
    )

    @Test
    fun `모든 필수값이 있으면 생성된다`() {
        assertThatCode { address() }.doesNotThrowAnyException()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t", "\n"])
    fun `도로명 주소가 비어 있으면 거부한다`(blank: String) {
        assertInvalidInput({ address(roadAddress = blank) }, "도로명 주소")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t", "\n"])
    fun `수령인 이름이 비어 있으면 거부한다`(blank: String) {
        assertInvalidInput({ address(recipientName = blank) }, "수령인 이름")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t", "\n"])
    fun `수령인 전화번호가 비어 있으면 거부한다`(blank: String) {
        assertInvalidInput({ address(recipientPhone = blank) }, "수령인 전화번호")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t", "\n"])
    fun `지역 코드가 비어 있으면 거부한다`(blank: String) {
        // 지역 코드는 배차 대상 캐리어를 고르는 축이라 비면 배차가 아무도 못 받는 상태가 된다.
        assertInvalidInput({ address(areaCode = blank) }, "지역 코드")
    }

    private fun assertInvalidInput(block: () -> Unit, messagePart: String) {
        assertThatThrownBy { block() }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining(messagePart)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.INVALID_INPUT)
    }

    @Test
    fun `선택값인 우편번호와 출입 정보는 없어도 된다`() {
        val withoutOptionals = OrderShippingAddress(
            roadAddress = "서울시 강남구 테헤란로 1",
            detailAddress = "101동 1001호",
            zipCode = null,
            latitude = 37.5,
            longitude = 127.0,
            recipientName = "홍길동",
            recipientPhone = "010-1234-5678",
            entranceInfo = null,
            areaCode = "GANGNAM",
        )

        assertThat(withoutOptionals.zipCode).isNull()
        assertThat(withoutOptionals.entranceInfo).isNull()
    }
}
