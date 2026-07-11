package com.carry.payment.domain.model

import com.carry.payment.domain.vo.ReconciliationMismatchType

/**
 * PG 대사에서 감지된 불일치 한 건 — 비파괴 원장 기록(자동 보정 없음, 운영 알럿/수동 개입 진입점).
 *
 * [dedupKey]는 (type, dedupKey) 로 재실행 중복 적재를 막는 결정적 키:
 * pgTransactionId 가 있으면 그것, 없으면 "payment:{paymentId}".
 */
data class ReconciliationMismatch(
    val type: ReconciliationMismatchType,
    val dedupKey: String,
    val paymentId: Long?,
    val orderId: Long?,
    val pgTransactionId: String?,
    val localAmount: Long?,
    val pgAmount: Long?,
    val detail: String,
)
