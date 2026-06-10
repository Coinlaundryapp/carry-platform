package com.carry.infra.redis

import java.util.concurrent.ConcurrentHashMap

/**
 * [IdempotencyStore] 인메모리 fallback(비분산, dev/test). 단일 프로세스 내 원자성만 보장하며
 * 다중 인스턴스 공유·TTL 만료가 없다. 분산 환경에선 [RedisIdempotencyStore]를 쓴다.
 * `RefreshTokenStoreConfig`의 InMemory fallback 선례와 동일한 정책이다.
 */
class InMemoryIdempotencyStore : IdempotencyStore {

    private val completed = ConcurrentHashMap<String, Long>()
    private val pending = ConcurrentHashMap.newKeySet<String>()

    override fun reserve(key: String): Boolean {
        if (completed.containsKey(key)) return false
        return pending.add(key) // 원자적: 처음 추가될 때만 true
    }

    override fun findCompletedId(key: String): Long? = completed[key]

    override fun complete(key: String, id: Long) {
        completed[key] = id
        pending.remove(key)
    }
}
