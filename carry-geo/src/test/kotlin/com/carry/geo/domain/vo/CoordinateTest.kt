package com.carry.geo.domain.vo

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CoordinateTest {

    @Test
    fun `유효한 좌표를 생성한다`() {
        val coord = Coordinate(37.5665, 126.9780)

        assertThat(coord.latitude).isEqualTo(37.5665)
        assertThat(coord.longitude).isEqualTo(126.9780)
    }

    @Test
    fun `경계값 좌표를 생성할 수 있다`() {
        val coord = Coordinate(90.0, 180.0)
        assertThat(coord.latitude).isEqualTo(90.0)
        assertThat(coord.longitude).isEqualTo(180.0)

        val negCoord = Coordinate(-90.0, -180.0)
        assertThat(negCoord.latitude).isEqualTo(-90.0)
        assertThat(negCoord.longitude).isEqualTo(-180.0)
    }

    @Test
    fun `위도가 범위를 벗어나면 예외가 발생한다`() {
        assertThatThrownBy { Coordinate(91.0, 126.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("위도")
    }

    @Test
    fun `경도가 범위를 벗어나면 예외가 발생한다`() {
        assertThatThrownBy { Coordinate(37.0, 181.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("경도")
    }

    @Test
    fun `네이버 API 좌표 형식으로 변환한다`() {
        val coord = Coordinate(37.5665, 126.9780)
        assertThat(coord.toNaverCoordsFormat()).isEqualTo("126.978,37.5665")
    }
}
