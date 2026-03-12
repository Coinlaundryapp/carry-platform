package com.carry.geo.application.service

import com.carry.geo.application.port.outbound.ReverseGeocodingPort
import com.carry.geo.domain.model.ReverseGeocodingResult
import com.carry.geo.domain.vo.Coordinate
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReverseGeocodingServiceTest {

    private val reverseGeocodingPort = mockk<ReverseGeocodingPort>()
    private val sut = ReverseGeocodingService(reverseGeocodingPort)

    @Test
    fun `좌표로 역지오코딩한다`() {
        val coordinate = Coordinate(37.5665, 126.9780)
        val expected = ReverseGeocodingResult(
            country = "대한민국",
            si = "서울특별시",
            gu = "중구",
            dong = "명동",
        )
        every { reverseGeocodingPort.reverseGeocode(coordinate) } returns expected

        val result = sut.reverseGeocode(coordinate)

        assertThat(result.country).isEqualTo("대한민국")
        assertThat(result.si).isEqualTo("서울특별시")
        assertThat(result.gu).isEqualTo("중구")
        assertThat(result.dong).isEqualTo("명동")
    }
}
