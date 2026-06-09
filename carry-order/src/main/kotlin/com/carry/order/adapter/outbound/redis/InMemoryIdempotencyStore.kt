package com.carry.order.adapter.outbound.redis

import com.carry.order.application.port.outbound.IdempotencyPort
import java.util.concurrent.ConcurrentHashMap

/**
 * [IdempotencyPort] 의 인메모리 fallback(비분산). Redis 미가용(dev/test) 시 사용된다.
 *
 * 단일 프로세스 내에서는 멱등성을 올바르게 보장하지만, 다중 인스턴스 간에는 공유되지 않고
 * TTL 만료도 없다(프로세스 메모리). 분산 환경에선 [RedisIdempotencyAdapter] 가 쓰인다.
 * `RefreshTokenStoreConfig` 의 InMemory fallback 선례와 동일한 정책이다.
 */
class InMemoryIdempotencyStore : IdempotencyPort {

    private val completed = ConcurrentHashMap<String, Long>()
    private val pending = ConcurrentHashMap.newKeySet<String>()

    override fun reserve(key: String): Boolean {
        if (completed.containsKey(key)) return false
        return pending.add(key) // 원자적: 처음 추가될 때만 true
    }

    override fun findCompletedOrderId(key: String): Long? = completed[key]

    override fun complete(key: String, orderId: Long) {
        completed[key] = orderId
        pending.remove(key)
    }
}
