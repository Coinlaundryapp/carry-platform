package com.carry.payment.adapter.outbound.redis

import com.carry.infra.redis.IdempotencyStore
import com.carry.payment.application.port.outbound.PaymentIdempotencyPort

/**
 * [PaymentIdempotencyPort]를 공유 [IdempotencyStore]에 위임 구현한다. 와이어링·키 프리픽스는
 * [PaymentIdempotencyConfig]가 담당한다.
 */
class PaymentIdempotencyAdapter(
    private val store: IdempotencyStore,
) : PaymentIdempotencyPort {
    override fun reserve(key: String): Boolean = store.reserve(key)
    override fun findCompletedPaymentId(key: String): Long? = store.findCompletedId(key)
    override fun complete(key: String, paymentId: Long) = store.complete(key, paymentId)
    override fun release(key: String) = store.release(key)
}
