package com.carry.app

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory

/**
 * 분산 스케줄러 락(ShedLock) 와이어링.
 *
 * @Scheduled 스위퍼들(배차 타임아웃·재결제 시한·정체 사가)이 멀티 인스턴스에서 동시에 돌면
 * 중복 실행된다(도메인 가드로 멱등하긴 하나 낭비). @SchedulerLock 으로 매 주기 한 노드만 실행되게 한다.
 *
 * 락 저장소는 Redis. test 프로파일은 RedisAutoConfiguration 을 제외하므로
 * [RefreshTokenStoreConfig] 선례대로 nullable [RedisConnectionFactory] 로 graceful degradation —
 * Redis 부재 시 [NoOpLockProvider](단일 인스턴스/test)로 떨어진다.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class ShedLockConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun lockProvider(
        @Autowired(required = false) connectionFactory: RedisConnectionFactory?,
    ): LockProvider {
        return if (connectionFactory != null) {
            RedisLockProvider(connectionFactory, "carry")
        } else {
            log.warn(
                "RedisConnectionFactory not available — ShedLock runs NoOp (non-distributed, dev/test only). " +
                    "분산 환경에선 Redis 락이 필요합니다.",
            )
            NoOpLockProvider()
        }
    }
}
