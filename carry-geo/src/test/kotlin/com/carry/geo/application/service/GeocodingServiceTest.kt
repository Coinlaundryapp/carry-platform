package com.carry.geo.application.service

import com.carry.geo.application.port.outbound.GeocodingPort
import com.carry.geo.domain.model.GeocodingResult
import com.carry.geo.domain.vo.AddressComponent
import com.carry.geo.domain.vo.Coordinate
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GeocodingServiceTest {

    private val geocodingPort = mockk<GeocodingPort>()
    private val sut = GeocodingService(geocodingPort)

    private fun aResult(
        jibunAddress: String = "서울특별시 중구 명동 1-1",
        roadAddress: String = "서울특별시 중구 명동길 10",
    ) = GeocodingResult(
        jibunAddress = jibunAddress,
        roadAddress = roadAddress,
        coordinate = Coordinate(37.5665, 126.9780),
        addressComponent = AddressComponent(
            sido = "서울특별시", sigungu = "중구", dongmyun = "명동",
            ri = null, roadName = "명동길", buildingName = null,
            landNumber = "1-1", postalCode = "04536",
        ),
    )

    @Nested
    inner class Geocode {

        @Test
        fun `주소로 지오코딩한다`() {
            every { geocodingPort.geocode("명동") } returns listOf(aResult())

            val results = sut.geocode("명동")

            assertThat(results).hasSize(1)
            assertThat(results[0].coordinate.latitude).isEqualTo(37.5665)
        }

        @Test
        fun `빈 주소로 호출하면 예외가 발생한다`() {
            assertThatThrownBy { sut.geocode("") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("비어있을 수 없습니다")
        }

        @Test
        fun `공백만 있는 주소로 호출하면 예외가 발생한다`() {
            assertThatThrownBy { sut.geocode("   ") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class GeocodeJibun {

        @Test
        fun `지번 주소가 있는 결과만 반환한다`() {
            every { geocodingPort.geocode("명동") } returns listOf(
                aResult(jibunAddress = "서울특별시 중구 명동 1-1", roadAddress = "서울특별시 중구 명동길 10"),
                aResult(jibunAddress = "", roadAddress = "서울특별시 중구 명동길 20"),
            )

            val results = sut.geocodeJibun("명동")

            assertThat(results).hasSize(1)
            assertThat(results[0].jibunAddress).isEqualTo("서울특별시 중구 명동 1-1")
        }
    }

    @Nested
    inner class GeocodeRoad {

        @Test
        fun `도로명 주소가 있는 결과만 반환한다`() {
            every { geocodingPort.geocode("명동") } returns listOf(
                aResult(jibunAddress = "서울특별시 중구 명동 1-1", roadAddress = ""),
                aResult(jibunAddress = "", roadAddress = "서울특별시 중구 명동길 20"),
            )

            val results = sut.geocodeRoad("명동")

            assertThat(results).hasSize(1)
            assertThat(results[0].roadAddress).isEqualTo("서울특별시 중구 명동길 20")
        }
    }
}
