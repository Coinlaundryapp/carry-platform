package com.carry.payment.application.port.outbound

/**
 * 결제 요청 명령측 멱등성 아웃바운드 포트. 공유 [com.carry.infra.redis.IdempotencyStore]를
 * 어댑터가 결제 키 프리픽스로 위임 구현한다.
 */
interface PaymentIdempotencyPort {
    fun reserve(key: String): Boolean
    fun findCompletedPaymentId(key: String): Long?
    fun complete(key: String, paymentId: Long)
}
