package com.carry.review.adapter.outbound.redis

import com.carry.infra.redis.InMemoryIdempotencyStore
import com.carry.infra.redis.RedisIdempotencyStore
import com.carry.review.application.port.outbound.ReviewIdempotencyPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

/**
 * [ReviewIdempotencyPort] 와이어링. nullable [StringRedisTemplate]로 graceful degradation
 * (Redis 부재 시 인메모리 fallback) — order/payment와 동형.
 */
@Configuration
class ReviewIdempotencyConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun reviewIdempotencyPort(
        @Autowired(required = false) stringRedisTemplate: StringRedisTemplate?,
        @Value("\${carry.idempotency.pending-ttl-seconds:120}") pendingTtlSeconds: Long,
        @Value("\${carry.idempotency.result-ttl-hours:24}") resultTtlHours: Long,
    ): ReviewIdempotencyPort {
        val store = if (stringRedisTemplate != null) {
            RedisIdempotencyStore(
                redis = stringRedisTemplate,
                keyPrefix = "idem:review:create:",
                pendingTtl = Duration.ofSeconds(pendingTtlSeconds),
                resultTtl = Duration.ofHours(resultTtlHours),
            )
        } else {
            log.warn(
                "StringRedisTemplate not available — ReviewIdempotency runs in-memory " +
                    "(non-distributed, dev/test only). 분산 환경에선 Redis가 필요합니다.",
            )
            InMemoryIdempotencyStore()
        }
        return ReviewIdempotencyAdapter(store)
    }
}
