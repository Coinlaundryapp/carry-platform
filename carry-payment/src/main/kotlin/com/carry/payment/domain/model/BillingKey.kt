package com.carry.payment.domain.model

import com.carry.payment.domain.vo.BillingKeyStatus
import java.time.Instant

/**
 * 고객의 자동결제 수단. billingKey 는 유출 시 임의 과금이 가능한 크리덴셜 —
 * 영속화 시 반드시 암호화(BillingKeyCryptoConverter)된다.
 * customerKey 는 PG 에 전달하는 고객 식별자로, 추측 불가능한 랜덤(UUID)이어야 한다.
 */
class BillingKey private constructor(
    val id: Long?,
    val customerId: Long,
    val customerKey: String,
    val billingKey: String,
    val cardCompany: String,
    val cardLast4: String,
    private var _status: BillingKeyStatus,
    private var _invalidatedAt: Instant?,
    val createdAt: Instant,
) {
    val status: BillingKeyStatus get() = _status
    val invalidatedAt: Instant? get() = _invalidatedAt

    fun invalidate(now: Instant) {
        check(_status == BillingKeyStatus.ACTIVE) { "이미 무효화된 빌링키입니다: id=$id" }
        _status = BillingKeyStatus.INVALID
        _invalidatedAt = now
    }

    companion object {
        fun create(
            customerId: Long, customerKey: String, billingKey: String,
            cardCompany: String, cardLast4: String, now: Instant,
        ): BillingKey {
            require(cardLast4.length == 4) { "cardLast4 는 4자리여야 합니다" }
            return BillingKey(null, customerId, customerKey, billingKey, cardCompany, cardLast4,
                BillingKeyStatus.ACTIVE, null, now)
        }

        fun reconstitute(
            id: Long, customerId: Long, customerKey: String, billingKey: String,
            cardCompany: String, cardLast4: String, status: BillingKeyStatus,
            invalidatedAt: Instant?, createdAt: Instant,
        ): BillingKey = BillingKey(id, customerId, customerKey, billingKey, cardCompany, cardLast4,
            status, invalidatedAt, createdAt)
    }
}
