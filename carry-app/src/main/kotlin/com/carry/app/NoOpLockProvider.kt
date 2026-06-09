package com.carry.app

import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.SimpleLock
import java.util.Optional

/**
 * Redis 미가용(test·단일 인스턴스) 시 사용하는 no-op ShedLock 프로바이더.
 *
 * 항상 락을 획득한 것으로 처리하고 해제는 무동작 → 스케줄러가 평소처럼 실행된다.
 * 단일 인스턴스에선 어차피 경합이 없으므로 정상 동작이며, 분산 환경에선
 * [net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider] 가 쓰인다([ShedLockConfig]).
 */
class NoOpLockProvider : LockProvider {

    override fun lock(lockConfiguration: LockConfiguration): Optional<SimpleLock> = Optional.of(NoOpLock)

    private object NoOpLock : SimpleLock {
        override fun unlock() {
            // no-op
        }
    }
}
