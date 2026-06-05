package com.carry.laundromat.domain.vo

import com.carry.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LocationTest {

    @Test
    fun `유효한 좌표를 생성한다`() {
        val location = Location(37.5665, 126.9780)
        assertThat(location.latitude).isEqualTo(37.5665)
        assertThat(location.longitude).isEqualTo(126.9780)
    }

    @Test
    fun `위도 범위를 초과하면 거부한다`() {
        assertThatThrownBy { Location(91.0, 0.0) }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `경도 범위를 초과하면 거부한다`() {
        assertThatThrownBy { Location(0.0, 181.0) }
            .isInstanceOf(BusinessException::class.java)
    }
}
