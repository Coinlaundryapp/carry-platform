package com.carry.audit.adapter.outbound.persistence

import com.carry.audit.domain.AuditLog
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * audit_logs 매핑. append-only 불변이라 BaseEntity(updated_at 보유)를 상속하지 않고
 * 자체 @Id + createdAt만 둔다(엔티티↔V19 DDL 정확 일치 — validate 프로파일 호환).
 * before/after는 outbox_events 선례대로 직렬화된 JSON String을 JSONB 컬럼에 매핑.
 */
@Entity
@Table(name = "audit_logs")
class AuditLogJpaEntity(
    @Column(nullable = false)
    val actor: String,

    @Column
    val role: String?,

    @Column(nullable = false)
    val action: String,

    @Column(name = "target_type", nullable = false)
    val targetType: String,

    @Column(name = "target_id", nullable = false)
    val targetId: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val before: String?,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val after: String?,

    @Column
    val ip: String?,

    @Column(name = "trace_id")
    val traceId: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    companion object {
        fun fromDomain(log: AuditLog): AuditLogJpaEntity = AuditLogJpaEntity(
            actor = log.actor,
            role = log.role,
            action = log.action.name,
            targetType = log.targetType,
            targetId = log.targetId,
            before = log.before,
            after = log.after,
            ip = log.ip,
            traceId = log.traceId,
            createdAt = log.occurredAt,
        )
    }
}
