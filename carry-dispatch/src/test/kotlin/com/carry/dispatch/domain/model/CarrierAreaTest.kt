package com.carry.dispatch.domain.model

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

/**
 * 캐리어 활동 구역 — `areaCode` 는 배차를 어떤 캐리어에게 노출할지 고르는 축이다.
 * 빈 값으로 등록되면 그 캐리어는 어떤 배차와도 매칭되지 않으면서 등록은 성공한 것처럼 보인다.
 *
 * 검증 자체는 처음부터 있었으나 `CarrierArea.create` 를 호출하는 테스트가 없었다(불변식 카탈로그 §6.3-9).
 */
class CarrierAreaTest {

    private val now = Instant.parse("2026-09-18T00:00:00Z")

    @Test
    fun `구역 코드가 있으면 활성 상태로 생성된다`() {
        val area = CarrierArea.create(carrierId = 7L, areaCode = "GANGNAM", areaName = "강남구", now = now)

        assertThat(area.carrierId).isEqualTo(7L)
        assertThat(area.areaCode).isEqualTo("GANGNAM")
        assertThat(area.active).isTrue()
        assertThat(area.id).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t", "\n"])
    fun `구역 코드가 비어 있으면 거부한다`(blank: String) {
        assertThatThrownBy { CarrierArea.create(carrierId = 7L, areaCode = blank, areaName = "강남구", now = now) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("구역 코드")
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.INVALID_INPUT)
    }

    @Test
    fun `비활성화와 재활성화가 상태에 반영된다`() {
        val area = CarrierArea.create(carrierId = 7L, areaCode = "GANGNAM", areaName = "강남구", now = now)

        area.deactivate()
        assertThat(area.active).isFalse()

        area.activate()
        assertThat(area.active).isTrue()
    }
}
