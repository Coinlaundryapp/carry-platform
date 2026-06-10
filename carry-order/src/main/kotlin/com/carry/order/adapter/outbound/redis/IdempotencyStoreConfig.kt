package com.carry.order.adapter.outbound.redis

import com.carry.infra.redis.InMemoryIdempotencyStore
import com.carry.infra.redis.RedisIdempotencyStore
import com.carry.order.application.port.outbound.IdempotencyPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

/**
 * [IdempotencyPort] 와이어링. 공유 [com.carry.infra.redis.IdempotencyStore](Redis/InMemory)를
 * 주문 키 프리픽스로 생성해 [OrderIdempotencyAdapter]로 래핑한다.
 *
 * `RefreshTokenStoreConfig` 선례를 따라 nullable [StringRedisTemplate]로 graceful degradation 한다.
 * [OrderCommandService]가 포트를 **필수 의존**으로 받는데 테스트 프로파일(`RedisAutoConfiguration` 제외)엔
 * [StringRedisTemplate] 빈이 없으므로, Redis 부재 시 인메모리 fallback을 항상 제공한다.
 */
@Configuration
class IdempotencyStoreConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun idempotencyPort(
        @Autowired(required = false) stringRedisTemplate: StringRedisTemplate?,
        @Value("\${carry.idempotency.pending-ttl-seconds:120}") pendingTtlSeconds: Long,
        @Value("\${carry.idempotency.result-ttl-hours:24}") resultTtlHours: Long,
    ): IdempotencyPort {
        val store = if (stringRedisTemplate != null) {
            RedisIdempotencyStore(
                redis = stringRedisTemplate,
                keyPrefix = "idem:order:create:",
                pendingTtl = Duration.ofSeconds(pendingTtlSeconds),
                resultTtl = Duration.ofHours(resultTtlHours),
            )
        } else {
            log.warn(
                "StringRedisTemplate not available — IdempotencyPort runs in-memory " +
                    "(non-distributed, dev/test only). 분산 환경에선 Redis가 필요합니다.",
            )
            InMemoryIdempotencyStore()
        }
        return OrderIdempotencyAdapter(store)
    }
}
