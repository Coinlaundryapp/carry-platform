package com.carry.audit.adapter.outbound.persistence

import com.carry.audit.domain.AuditAction
import com.carry.audit.domain.AuditLog
import com.carry.audit.port.AuditPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant

/**
 * [AuditPort] 구현. 호출 시점(같은 요청 스레드)의 ambient 컨텍스트에서 actor/role/ip/traceId를
 * 수집하고 before/after를 JSON으로 직렬화해 같은 트랜잭션으로 audit_logs에 기록한다.
 *
 * best-effort: context 해석·직렬화 실패는 throw 없이 degrade(actor=SYSTEM, role/ip=null,
 * 직렬화 실패 시 해당 값 null). 컬럼 한도 초과 값은 truncate해 INSERT가 액션을 깨지 않게 한다.
 * (INSERT 자체 실패는 같은 tx라 설계상 액션과 함께 롤백 — 일관성 우선.)
 */
@Component
class AuditPersistenceAdapter(
    private val repository: AuditLogJpaRepository,
    private val objectMapper: ObjectMapper,
) : AuditPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun record(action: AuditAction, targetType: String, targetId: String, before: Any?, after: Any?) {
        val entry = AuditLog(
            action = action,
            targetType = targetType.take(MAX_64),
            targetId = targetId.take(MAX_64),
            before = serialize(before),
            after = serialize(after),
            actor = resolveActor(),
            role = resolveRole()?.take(MAX_32),
            ip = resolveIp()?.take(MAX_64),
            traceId = MDC.get(TRACE_ID),
            occurredAt = Instant.now(),
        )
        repository.save(AuditLogJpaEntity.fromDomain(entry))
    }

    private fun serialize(value: Any?): String? {
        if (value == null) return null
        return try {
            objectMapper.writeValueAsString(value)
        } catch (e: Exception) {
            log.warn("감사 before/after 직렬화 실패 — null로 degrade", e)
            null
        }
    }

    /** principal이 Long(실 사용자)일 때만 actor=userId. 미인증/익명/비-Long은 SYSTEM. */
    private fun resolveActor(): String =
        (SecurityContextHolder.getContext().authentication?.principal as? Long)?.toString()?.take(MAX_64) ?: SYSTEM

    private fun resolveRole(): String? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null
        if (auth.principal !is Long) return null
        return auth.authorities.firstOrNull { it.authority.startsWith(ROLE_PREFIX) }
            ?.authority?.removePrefix(ROLE_PREFIX)
    }

    /** 요청 스레드에서만 IP 수집(saga/@Async 스레드엔 request가 없어 null). */
    private fun resolveIp(): String? {
        val attrs = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes ?: return null
        val req = attrs.request
        return req.getHeader(X_FORWARDED_FOR)?.substringBefore(",")?.trim()?.takeIf { it.isNotBlank() }
            ?: req.remoteAddr
    }

    companion object {
        private const val SYSTEM = "SYSTEM"
        private const val ROLE_PREFIX = "ROLE_"
        private const val TRACE_ID = "traceId"
        private const val X_FORWARDED_FOR = "X-Forwarded-For"
        private const val MAX_64 = 64
        private const val MAX_32 = 32
    }
}
