package com.carry.geo.adapter.outbound.config

import com.carry.common.metrics.MetricsPort
import com.carry.geo.adapter.outbound.cache.RedisCachingGeocodingAdapter
import com.carry.geo.adapter.outbound.cache.RedisCachingReverseGeocodingAdapter
import com.carry.geo.adapter.outbound.external.naver.NaverApiProperties
import com.carry.geo.adapter.outbound.resilience.CircuitBreakerGeocodingAdapter
import com.carry.geo.adapter.outbound.resilience.CircuitBreakerReverseGeocodingAdapter
import com.carry.geo.application.port.outbound.GeocodingPort
import com.carry.geo.application.port.outbound.ReverseGeocodingPort
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.data.redis.connection.RedisConnectionFactory

/**
 * 지오코딩 데코레이터 체인 와이어링 회귀 가드.
 *
 * 배경(2026-06-12 라이브 스모크 발견): 캐시 층 활성 조건이 `RedisConfig`(@Configuration +
 * `@ConditionalOnBean(RedisConnectionFactory)`)가 만드는 `RedisTemplate` 빈에 걸려 있었는데,
 * `@ConditionalOnBean`은 자동구성보다 먼저 평가되는 사용자 @Configuration에선 신뢰할 수 없어
 * **Redis가 떠 있는 로컬/운영 구동에서도 캐시 층이 조용히 빠졌다**("RedisTemplate not available"
 * 경고). 이 테스트는 "팩토리가 있으면 캐시 층이 반드시 활성"을 컨텍스트 수준에서 잠근다.
 */
class GeocodingResilienceConfigTest {

    private val runner = ApplicationContextRunner()
        .withBean(NaverApiProperties::class.java, { NaverApiProperties(clientId = "id", clientSecret = "secret") })
        .withBean(
            CircuitBreakerRegistry::class.java,
            { CircuitBreakerRegistry.of(mapOf("geocoding" to CircuitBreakerConfig.ofDefaults())) },
        )
        .withBean(MetricsPort::class.java, { mockk<MetricsPort>(relaxed = true) })
        .withUserConfiguration(GeocodingResilienceConfig::class.java)

    @Test
    fun `RedisConnectionFactory가 있으면 캐시 층이 활성화된다 — fwd·rev 모두`() {
        runner
            .withBean(RedisConnectionFactory::class.java, { mockk<RedisConnectionFactory>(relaxed = true) })
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(ctx.getBean(GeocodingPort::class.java))
                    .isInstanceOf(RedisCachingGeocodingAdapter::class.java)
                assertThat(ctx.getBean(ReverseGeocodingPort::class.java))
                    .isInstanceOf(RedisCachingReverseGeocodingAdapter::class.java)
            }
    }

    @Test
    fun `RedisConnectionFactory가 없으면 캐시 층 없이 CB만으로 동작한다 — graceful degradation`() {
        runner.run { ctx ->
            assertThat(ctx).hasNotFailed()
            assertThat(ctx.getBean(GeocodingPort::class.java))
                .isInstanceOf(CircuitBreakerGeocodingAdapter::class.java)
            assertThat(ctx.getBean(ReverseGeocodingPort::class.java))
                .isInstanceOf(CircuitBreakerReverseGeocodingAdapter::class.java)
        }
    }
}
