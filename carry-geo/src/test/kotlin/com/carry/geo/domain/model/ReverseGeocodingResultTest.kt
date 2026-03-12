package com.carry.geo.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReverseGeocodingResultTest {

    @Test
    fun `전체 주소를 생성한다`() {
        val result = ReverseGeocodingResult(
            country = "대한민국",
            si = "서울특별시",
            gu = "중구",
            dong = "명동",
        )

        assertThat(result.toFullAddress()).isEqualTo("서울특별시 중구 명동")
    }

    @Test
    fun `빈 항목은 제외하고 전체 주소를 생성한다`() {
        val result = ReverseGeocodingResult(
            country = "대한민국",
            si = "세종특별자치시",
            gu = "",
            dong = "어진동",
        )

        assertThat(result.toFullAddress()).isEqualTo("세종특별자치시 어진동")
    }
}
