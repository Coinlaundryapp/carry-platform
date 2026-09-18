package com.carry.order.domain.vo

import java.time.Instant

/**
 * 주문 취소 정보 값 객체 — 취소는 사유·주체·시각이 항상 함께 정해지는 하나의 사실이다.
 *
 * 이전엔 [com.carry.order.domain.model.Order] 가 cancelReason/cancelledBy/cancelledAt 세 스칼라를
 * 따로 들고 reconstitute 파라미터도 셋으로 늘어났다(ROADMAP 6.4). 응집적인 한 개념으로 묶어
 * "셋 다 있거나 셋 다 없음" 불변식을 타입으로 표현한다.
 */
data class OrderCancellation(
    val reason: String,
    val by: CancelledBy,
    val at: Instant,
)
