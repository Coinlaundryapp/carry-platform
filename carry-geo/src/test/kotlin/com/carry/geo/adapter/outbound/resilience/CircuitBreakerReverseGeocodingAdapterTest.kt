package com.carry.geo.adapter.outbound.resilience

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.geo.application.port.outbound.ReverseGeocodingPort
import com.carry.geo.domain.exception.ReverseGeocodingFailedException
import com.carry.geo.domain.model.ReverseGeocodingResult
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

class CircuitBreakerReverseGeocodingAdapterTest {

    private val sampleCoord = Coordinate(latitude = 37.5, longitude = 127.0)
    private val sampleResult = ReverseGeocodingResult(
        country = "대한민국",
        si = "서울특별시",
        gu = "강남구",
        dong = "역삼동",
    )

    private fun circuitBreaker(windowSize: Int = 4, failureRate: Float = 50f): CircuitBreaker {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(windowSize)
            .minimumNumberOfCalls(windowSize)
            .failureRateThreshold(failureRate)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .build()
        return CircuitBreaker.of("test-reverse-geocoding", config)
    }

    @Test
    fun `정상 호출은 delegate에 그대로 위임된다`() {
        val delegate = mockk<ReverseGeocodingPort>()
        every { delegate.reverseGeocode(any()) } returns sampleResult
        val sut = CircuitBreakerReverseGeocodingAdapter(delegate, circuitBreaker())

        val result = sut.reverseGeocode(sampleCoord)

        assertThat(result.dong).isEqualTo("역삼동")
        verify(exactly = 1) { delegate.reverseGeocode(sampleCoord) }
    }

    @Test
    fun `delegate가 던지는 예외는 그대로 전파된다 — circuit이 CLOSED일 때`() {
        val delegate = mockk<ReverseGeocodingPort>()
        every { delegate.reverseGeocode(any()) } throws ReverseGeocodingFailedException("Naver returned 500")
        val sut = CircuitBreakerReverseGeocodingAdapter(delegate, circuitBreaker())

        assertThatThrownBy { sut.reverseGeocode(sampleCoord) }
            .isInstanceOf(ReverseGeocodingFailedException::class.java)
            .hasMessageContaining("Naver returned 500")
    }

    @Test
    fun `실패가 임계치를 넘으면 circuit이 OPEN되고 이후 호출은 BusinessException(GEOCODING_UNAVAILABLE)로 fast-fail한다`() {
        val delegate = mockk<ReverseGeocodingPort>()
        every { delegate.reverseGeocode(any()) } throws RuntimeException("Naver down")
        val cb = circuitBreaker(windowSize = 4, failureRate = 50f)
        val sut = CircuitBreakerReverseGeocodingAdapter(delegate, cb)

        repeat(4) {
            assertThatThrownBy { sut.reverseGeocode(sampleCoord) }
                .isInstanceOf(RuntimeException::class.java)
        }

        assertThat(cb.state).isEqualTo(CircuitBreaker.State.OPEN)

        assertThatThrownBy { sut.reverseGeocode(sampleCoord) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode").isEqualTo(ErrorCode.GEOCODING_UNAVAILABLE)

        verify(exactly = 4) { delegate.reverseGeocode(any()) }
    }
}
