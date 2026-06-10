package com.carry.review.application.port.outbound

/**
 * 리뷰 작성 명령측 멱등성 아웃바운드 포트. 공유 [com.carry.infra.redis.IdempotencyStore]를
 * 어댑터가 리뷰 키 프리픽스로 위임 구현한다.
 */
interface ReviewIdempotencyPort {
    fun reserve(key: String): Boolean
    fun findCompletedReviewId(key: String): Long?
    fun complete(key: String, reviewId: Long)
}
