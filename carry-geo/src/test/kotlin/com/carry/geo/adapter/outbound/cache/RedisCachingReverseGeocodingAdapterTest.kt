package com.carry.geo.adapter.outbound.cache

import com.carry.common.metrics.MetricsPort
import com.carry.geo.application.port.outbound.ReverseGeocodingPort
import com.carry.geo.domain.exception.ReverseGeocodingFailedException
import com.carry.geo.domain.model.ReverseGeocodingResult
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

class RedisCachingReverseGeocodingAdapterTest {

    private val metrics = mockk<MetricsPort>(relaxed = true)

    private val sampleCoord = Coordinate(latitude = 37.501234, longitude = 127.039876)
    private val sampleResult = ReverseGeocodingResult(
        country = "대한민국",
        si = "서울특별시",
        gu = "강남구",
        dong = "역삼동",
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
        val delegate = mockk<ReverseGeocodingPort>()
        every { delegate.reverseGeocode(any()) } returns sampleResult
        val (redisTemplate, valueOps) = mockRedisTemplate()
        every { valueOps.get(any()) } returns null
        every { valueOps.set(any(), any(), any<Duration>()) } returns Unit
        val sut = RedisCachingReverseGeocodingAdapter(delegate, redisTemplate, metrics)

        val result = sut.reverseGeocode(sampleCoord)

        assertThat(result.dong).isEqualTo("역삼동")
        verify(exactly = 1) { delegate.reverseGeocode(sampleCoord) }
        verify(exactly = 1) {
            valueOps.set("geo:rev:37.501234:127.039876", sampleResult, Duration.ofHours(24))
        }
    }

    @Test
    fun `cache hit 시 delegate를 호출하지 않고 캐시 값을 반환한다`() {
        val delegate = mockk<ReverseGeocodingPort>()
        val (redisTemplate, valueOps) = mockRedisTemplate()
        every { valueOps.get("geo:rev:37.501234:127.039876") } returns sampleResult
        val sut = RedisCachingReverseGeocodingAdapter(delegate, redisTemplate, metrics)

        val result = sut.reverseGeocode(sampleCoord)

        assertThat(result.dong).isEqualTo("역삼동")
        verify(exactly = 0) { delegate.reverseGeocode(any()) }
    }

    @Test
    fun `cache key는 좌표를 6자리 소수점으로 반올림해 정규화한다 — 미세한 부동소수 차이가 키 폭발을 일으키지 않는다`() {
        val delegate = mockk<ReverseGeocodingPort>()
        every { delegate.reverseGeocode(any()) } returns sampleResult
        val (redisTemplate, valueOps) = mockRedisTemplate()
        every { valueOps.get(any()) } returns null
        every { valueOps.set(any(), any(), any<Duration>()) } returns Unit
        val sut = RedisCachingReverseGeocodingAdapter(delegate, redisTemplate, metrics)

        // 7자리째에서만 차이나는 좌표 — 같은 키로 정규화돼야 한다
        sut.reverseGeocode(Coordinate(latitude = 37.5012341, longitude = 127.0398761))

        verify(exactly = 1) { valueOps.get("geo:rev:37.501234:127.039876") }
    }

    @Test
    fun `cache hit-miss 시 carry_geo_cache 메트릭을 direction=rev로 기록한다`() {
        val delegate = mockk<ReverseGeocodingPort>()
        every { delegate.reverseGeocode(any()) } returns sampleResult
        val (redisTemplate, valueOps) = mockRedisTemplate()
        every { valueOps.get(any()) } returns null andThen sampleResult
        every { valueOps.set(any(), any(), any<Duration>()) } returns Unit
        val sut = RedisCachingReverseGeocodingAdapter(delegate, redisTemplate, metrics)

        sut.reverseGeocode(sampleCoord) // miss
        sut.reverseGeocode(sampleCoord) // hit

        verify(exactly = 1) {
            metrics.incrementCounter("carry.geo.cache", "direction" to "rev", "result" to "miss")
        }
        verify(exactly = 1) {
            metrics.incrementCounter("carry.geo.cache", "direction" to "rev", "result" to "hit")
        }
    }

    @Test
    fun `delegate 예외는 그대로 전파하고 캐시하지 않는다`() {
        val delegate = mockk<ReverseGeocodingPort>()
        every { delegate.reverseGeocode(any()) } throws ReverseGeocodingFailedException("Naver returned 500")
        val (redisTemplate, valueOps) = mockRedisTemplate()
        every { valueOps.get(any()) } returns null
        val sut = RedisCachingReverseGeocodingAdapter(delegate, redisTemplate, metrics)

        assertThatThrownBy { sut.reverseGeocode(sampleCoord) }
            .isInstanceOf(ReverseGeocodingFailedException::class.java)

        verify(exactly = 0) { valueOps.set(any(), any(), any<Duration>()) }
    }
}
