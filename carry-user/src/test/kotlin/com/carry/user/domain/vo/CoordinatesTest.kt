package com.carry.user.domain.vo

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CoordinatesTest {

    @Test
    fun `유효한 좌표를 생성한다`() {
        val coords = Coordinates(37.5665, 126.9780)
        assertThat(coords.latitude).isEqualTo(37.5665)
        assertThat(coords.longitude).isEqualTo(126.9780)
    }

    @Test
    fun `경계값 좌표를 허용한다`() {
        assertThat(Coordinates(90.0, 180.0).latitude).isEqualTo(90.0)
        assertThat(Coordinates(-90.0, -180.0).longitude).isEqualTo(-180.0)
    }

    @Test
    fun `위도 범위를 초과하면 거부한다`() {
        assertThatThrownBy { Coordinates(91.0, 0.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("위도")
    }

    @Test
    fun `경도 범위를 초과하면 거부한다`() {
        assertThatThrownBy { Coordinates(0.0, 181.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("경도")
    }
}
