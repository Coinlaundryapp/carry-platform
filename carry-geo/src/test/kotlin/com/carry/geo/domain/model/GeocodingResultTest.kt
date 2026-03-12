package com.carry.geo.domain.model

import com.carry.geo.domain.vo.AddressComponent
import com.carry.geo.domain.vo.Coordinate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeocodingResultTest {

    private fun aResult(
        jibunAddress: String = "서울특별시 중구 명동 1-1",
        roadAddress: String = "서울특별시 중구 명동길 10",
    ) = GeocodingResult(
        jibunAddress = jibunAddress,
        roadAddress = roadAddress,
        coordinate = Coordinate(37.5665, 126.9780),
        addressComponent = AddressComponent(
            sido = "서울특별시",
            sigungu = "중구",
            dongmyun = "명동",
            ri = null,
            roadName = "명동길",
            buildingName = null,
            landNumber = "1-1",
            postalCode = "04536",
        ),
    )

    @Test
    fun `지번 주소 존재 여부를 확인한다`() {
        assertThat(aResult().hasJibunAddress()).isTrue()
        assertThat(aResult(jibunAddress = "").hasJibunAddress()).isFalse()
        assertThat(aResult(jibunAddress = "  ").hasJibunAddress()).isFalse()
    }

    @Test
    fun `도로명 주소 존재 여부를 확인한다`() {
        assertThat(aResult().hasRoadAddress()).isTrue()
        assertThat(aResult(roadAddress = "").hasRoadAddress()).isFalse()
    }
}
