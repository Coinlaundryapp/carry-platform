package com.carry.order.adapter.outbound.redis

import com.carry.infra.redis.IdempotencyStore
import com.carry.order.application.port.outbound.IdempotencyPort

/**
 * [IdempotencyPort]를 공유 [IdempotencyStore]에 위임 구현한다. 와이어링·키 프리픽스는
 * [IdempotencyStoreConfig]가 담당한다.
 */
class OrderIdempotencyAdapter(
    private val store: IdempotencyStore,
) : IdempotencyPort {
    override fun reserve(key: String): Boolean = store.reserve(key)
    override fun findCompletedOrderId(key: String): Long? = store.findCompletedId(key)
    override fun complete(key: String, orderId: Long) = store.complete(key, orderId)
}
