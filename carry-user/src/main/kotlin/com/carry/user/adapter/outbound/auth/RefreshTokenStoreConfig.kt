package com.carry.user.adapter.outbound.auth

import com.carry.security.jwt.JwtProperties
import com.carry.user.application.port.outbound.RefreshTokenStorePort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * [RefreshTokenStorePort] 와이어링. geo `GeocodingResilienceConfig` 선례를 따라
 * nullable `StringRedisTemplate`로 graceful degradation한다.
 *
 * [AuthService]가 포트를 **필수 의존**으로 받는데, 테스트 프로파일(application-test.yml이
 * `RedisAutoConfiguration` 제외)엔 [StringRedisTemplate] 빈이 없다. 어댑터에 `@ConditionalOnBean`만
 * 달면 빈이 아예 없어 컨텍스트가 깨지므로, 여기서 Redis 부재 시 인메모리 fallback을 항상 제공한다.
 *
 * TTL·grace는 [JwtProperties]에서 읽어 JwtProvider와 source-of-truth를 공유한다.
 */
@Configuration
class RefreshTokenStoreConfig(
    private val jwtProperties: JwtProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun refreshTokenStorePort(
        @Autowired(required = false) stringRedisTemplate: StringRedisTemplate?,
    ): RefreshTokenStorePort {
        return if (stringRedisTemplate != null) {
            RedisRefreshTokenStore(
                redis = stringRedisTemplate,
                ttlMillis = jwtProperties.refreshTokenExpiration,
                graceMillis = jwtProperties.refreshTokenRotationGraceMillis,
            )
        } else {
            log.warn(
                "StringRedisTemplate not available — RefreshTokenStorePort runs in-memory " +
                    "(non-distributed, dev/test only). 분산 환경에선 Redis가 필요합니다.",
            )
            InMemoryRefreshTokenStore(graceMillis = jwtProperties.refreshTokenRotationGraceMillis)
        }
    }
}
