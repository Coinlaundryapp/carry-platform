package com.carry.geo.adapter.outbound.cache

import com.carry.geo.application.port.outbound.GeocodingPort
import com.carry.geo.domain.exception.GeocodingFailedException
import com.carry.geo.domain.model.GeocodingResult
import com.carry.geo.domain.vo.AddressComponent
import com.carry.geo.domain.vo.Coordinate
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class RedisCachingGeocodingAdapterTest {

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

    private fun mockRedisTemplate(): Pair<RedisTemplate<String, Any>, ValueOperations<String, Any>> {
        @Suppress("UNCHECKED_CAST")
        val redisTemplate = mockk<RedisTemplate<String, Any>>()
        @Suppress("UNCHECKED_CAST")
        val valueOps = mockk<ValueOperations<String, Any>>()
        every { redisTemplate.opsForValue() } returns valueOps
        return redisTemplate to valueOps
    }

    @Test
    fun `cache miss 시 delegate를 호출하고 결과를 24h TTL로 캐싱한다`() {
        val delegate = mockk<GeocodingPort>()
        every { delegate.geocode(any()) } returns listOf(sampleResult)
        val (redisTemplate, valueOps) = mockRedisTemplate()
        every { valueOps.get(any()) } returns null
        every { valueOps.set(any(), any(), any<Duration>()) } returns Unit
        val sut = RedisCachingGeocodingAdapter(delegate, redisTemplate)

        val result = sut.geocode("서울특별시 강남구 테헤란로 100")

        assertThat(result).hasSize(1)
        verify(exactly = 1) { delegate.geocode("서울특별시 강남구 테헤란로 100") }
        verify(exactly = 1) {
            valueOps.set("geo:fwd:서울특별시 강남구 테헤란로 100", listOf(sampleResult), Duration.ofHours(24))
        }
    }

    @Test
    fun `cache hit 시 delegate를 호출하지 않고 캐시 값을 반환한다`() {
        val delegate = mockk<GeocodingPort>()
        val (redisTemplate, valueOps) = mockRedisTemplate()
        every { valueOps.get("geo:fwd:서울특별시 강남구 테헤란로 100") } returns listOf(sampleResult)
        val sut = RedisCachingGeocodingAdapter(delegate, redisTemplate)

        val result = sut.geocode("서울특별시 강남구 테헤란로 100")

        assertThat(result).hasSize(1)
        assertThat(result.first().roadAddress).isEqualTo("서울특별시 강남구 테헤란로 100")
        verify(exactly = 0) { delegate.geocode(any()) }
    }

    @Test
    fun `cache key는 주소 양쪽 공백을 trim해 정규화한다`() {
        val delegate = mockk<GeocodingPort>()
        every { delegate.geocode(any()) } returns listOf(sampleResult)
        val (redisTemplate, valueOps) = mockRedisTemplate()
        every { valueOps.get("geo:fwd:서울특별시 강남구 테헤란로 100") } returns null
        every { valueOps.set(any(), any(), any<Duration>()) } returns Unit
        val sut = RedisCachingGeocodingAdapter(delegate, redisTemplate)

        sut.geocode("  서울특별시 강남구 테헤란로 100  ")

        verify(exactly = 1) { valueOps.get("geo:fwd:서울특별시 강남구 테헤란로 100") }
        verify(exactly = 1) {
            valueOps.set("geo:fwd:서울특별시 강남구 테헤란로 100", any(), Duration.ofHours(24))
        }
    }

    @Test
    fun `delegate 예외는 그대로 전파하고 캐시하지 않는다`() {
        val delegate = mockk<GeocodingPort>()
        every { delegate.geocode(any()) } throws GeocodingFailedException("Naver returned 500")
        val (redisTemplate, valueOps) = mockRedisTemplate()
        every { valueOps.get(any()) } returns null
        val sut = RedisCachingGeocodingAdapter(delegate, redisTemplate)

        assertThatThrownBy { sut.geocode("주소") }
            .isInstanceOf(GeocodingFailedException::class.java)

        verify(exactly = 0) { valueOps.set(any(), any(), any<Duration>()) }
    }

    @Test
    fun `빈 결과는 캐싱하지 않는다 — Naver가 일시적으로 빈 결과를 줄 때 음수 캐시를 피한다`() {
        val delegate = mockk<GeocodingPort>()
        every { delegate.geocode(any()) } returns emptyList()
        val (redisTemplate, valueOps) = mockRedisTemplate()
        every { valueOps.get(any()) } returns null
        val sut = RedisCachingGeocodingAdapter(delegate, redisTemplate)

        val result = sut.geocode("존재하지 않는 주소")

        assertThat(result).isEmpty()
        verify(exactly = 0) { valueOps.set(any(), any(), any<Duration>()) }
    }
}
