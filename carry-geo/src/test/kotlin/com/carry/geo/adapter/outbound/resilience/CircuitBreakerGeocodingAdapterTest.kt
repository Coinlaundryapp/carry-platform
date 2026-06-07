package com.carry.geo.adapter.outbound.resilience

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.geo.application.port.outbound.GeocodingPort
import com.carry.geo.domain.exception.GeocodingFailedException
import com.carry.geo.domain.model.GeocodingResult
import com.carry.geo.domain.vo.AddressComponent
import com.carry.geo.domain.vo.Coordinate
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

class CircuitBreakerGeocodingAdapterTest {

    private val sampleResult = GeocodingResult(
        jibunAddress = "서울특별시 강남구 역삼동 123",
        roadAddress = "서울특별시 강남구 테헤란로 100",
        coordinate = Coordinate(latitude = 37.5, longitude = 127.0),
        addressComponent = AddressComponent(
            sido = "서울특별시",
            sigungu = "강남구",
            dongmyun = "역삼동",
            ri = null,
            roadName = "테헤란로",
            buildingName = null,
            landNumber = "100",
            postalCode = null,
        ),
    )

    private fun circuitBreaker(windowSize: Int = 4, failureRate: Float = 50f): CircuitBreaker {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(windowSize)
            .minimumNumberOfCalls(windowSize)
            .failureRateThreshold(failureRate)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .build()
        return CircuitBreaker.of("test-geocoding", config)
    }

    @Test
    fun `정상 호출은 delegate에 그대로 위임된다`() {
        val delegate = mockk<GeocodingPort>()
        every { delegate.geocode(any()) } returns listOf(sampleResult)
        val sut = CircuitBreakerGeocodingAdapter(delegate, circuitBreaker())

        val result = sut.geocode("서울특별시 강남구 테헤란로 100")

        assertThat(result).hasSize(1)
        assertThat(result.first().roadAddress).isEqualTo("서울특별시 강남구 테헤란로 100")
        verify(exactly = 1) { delegate.geocode("서울특별시 강남구 테헤란로 100") }
    }

    @Test
    fun `delegate가 던지는 예외는 그대로 전파된다 — circuit이 CLOSED일 때`() {
        val delegate = mockk<GeocodingPort>()
        every { delegate.geocode(any()) } throws GeocodingFailedException("Naver returned 500")
        val sut = CircuitBreakerGeocodingAdapter(delegate, circuitBreaker())

        assertThatThrownBy { sut.geocode("주소") }
            .isInstanceOf(GeocodingFailedException::class.java)
            .hasMessageContaining("Naver returned 500")
    }

    @Test
    fun `실패가 임계치를 넘으면 circuit이 OPEN되고 이후 호출은 BusinessException(GEOCODING_UNAVAILABLE)로 fast-fail한다`() {
        val delegate = mockk<GeocodingPort>()
        every { delegate.geocode(any()) } throws RuntimeException("Naver down")
        val cb = circuitBreaker(windowSize = 4, failureRate = 50f)
        val sut = CircuitBreakerGeocodingAdapter(delegate, cb)

        // 4회 연속 실패 → 실패율 100% → OPEN
        repeat(4) {
            assertThatThrownBy { sut.geocode("주소") }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Naver down")
        }

        assertThat(cb.state).isEqualTo(CircuitBreaker.State.OPEN)

        // 다음 호출은 delegate에 도달하지 않고 즉시 BusinessException
        assertThatThrownBy { sut.geocode("주소") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode").isEqualTo(ErrorCode.GEOCODING_UNAVAILABLE)

        verify(exactly = 4) { delegate.geocode(any()) }
    }
}
