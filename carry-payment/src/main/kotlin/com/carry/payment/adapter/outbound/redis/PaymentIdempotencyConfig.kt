package com.carry.payment.adapter.outbound.redis

import com.carry.infra.redis.InMemoryIdempotencyStore
import com.carry.infra.redis.RedisIdempotencyStore
import com.carry.payment.application.port.outbound.PaymentIdempotencyPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

/**
 * [PaymentIdempotencyPort] 와이어링. order의 IdempotencyStoreConfig와 동형으로, nullable
 * [StringRedisTemplate]로 graceful degradation 한다(Redis 부재 시 인메모리 fallback).
 */
@Configuration
class PaymentIdempotencyConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun paymentIdempotencyPort(
        @Autowired(required = false) stringRedisTemplate: StringRedisTemplate?,
        @Value("\${carry.idempotency.pending-ttl-seconds:120}") pendingTtlSeconds: Long,
        @Value("\${carry.idempotency.result-ttl-hours:24}") resultTtlHours: Long,
    ): PaymentIdempotencyPort {
        val store = if (stringRedisTemplate != null) {
            RedisIdempotencyStore(
                redis = stringRedisTemplate,
                keyPrefix = "idem:payment:request:",
                pendingTtl = Duration.ofSeconds(pendingTtlSeconds),
                resultTtl = Duration.ofHours(resultTtlHours),
            )
        } else {
            log.warn(
                "StringRedisTemplate not available — PaymentIdempotency runs in-memory " +
                    "(non-distributed, dev/test only). 분산 환경에선 Redis가 필요합니다.",
            )
            InMemoryIdempotencyStore()
        }
        return PaymentIdempotencyAdapter(store)
    }
}
