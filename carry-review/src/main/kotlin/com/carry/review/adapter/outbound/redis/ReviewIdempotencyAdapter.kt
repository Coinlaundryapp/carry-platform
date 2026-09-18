package com.carry.review.adapter.outbound.redis

import com.carry.infra.redis.IdempotencyStore
import com.carry.review.application.port.outbound.ReviewIdempotencyPort

/**
 * [ReviewIdempotencyPort]를 공유 [IdempotencyStore]에 위임 구현한다. 와이어링·키 프리픽스는
 * [ReviewIdempotencyConfig]가 담당한다.
 */
class ReviewIdempotencyAdapter(
    private val store: IdempotencyStore,
) : ReviewIdempotencyPort {
    override fun reserve(key: String): Boolean = store.reserve(key)
    override fun findCompletedReviewId(key: String): Long? = store.findCompletedId(key)
    override fun complete(key: String, reviewId: Long) = store.complete(key, reviewId)
}
