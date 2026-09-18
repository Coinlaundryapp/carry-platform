package com.carry.audit.domain

import java.time.Instant

/**
 * 감사 로그 한 건. 누가(actor/role)·언제(occurredAt)·무엇을(action/target)·어떻게(before/after)
 * 바꿨는지 + 추적 컨텍스트(ip/traceId)를 담는 append-only 불변 기록.
 *
 * before/after는 어댑터가 직렬화한 JSON 문자열(또는 직렬화 실패 시 null).
 */
data class AuditLog(
    val action: AuditAction,
    val targetType: String,
    val targetId: String,
    val before: String?,
    val after: String?,
    val actor: String,
    val role: String?,
    val ip: String?,
    val traceId: String?,
    val occurredAt: Instant,
)
