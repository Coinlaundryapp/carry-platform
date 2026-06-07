package com.carry.audit.adapter.outbound.persistence

import com.carry.audit.domain.AuditAction
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

class AuditPersistenceAdapterTest {

    private val repository = mockk<AuditLogJpaRepository>()
    private val sut = AuditPersistenceAdapter(repository, ObjectMapper())

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
        MDC.clear()
        RequestContextHolder.resetRequestAttributes()
    }

    private fun authenticate(userId: Long, role: String) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_$role")))
    }

    private fun withRequest(forwardedFor: String? = null, remoteAddr: String = "10.0.0.1") {
        val req = MockHttpServletRequest()
        req.remoteAddr = remoteAddr
        if (forwardedFor != null) req.addHeader("X-Forwarded-For", forwardedFor)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(req))
    }

    @Test
    fun `인증 컨텍스트에서 actor·role·ip·traceId·before·after를 채운다`() {
        authenticate(42L, "COORDINATOR")
        withRequest(forwardedFor = "203.0.113.7, 10.0.0.1")
        MDC.put("traceId", "trace-abc")
        val slot = slot<AuditLogJpaEntity>()
        every { repository.save(capture(slot)) } answers { slot.captured }

        sut.record(
            AuditAction.ORDER_CANCEL, "ORDER", "100",
            before = mapOf("status" to "PAID"),
            after = mapOf("status" to "CANCELLED"),
        )

        val e = slot.captured
        assertThat(e.actor).isEqualTo("42")
        assertThat(e.role).isEqualTo("COORDINATOR")
        assertThat(e.action).isEqualTo("ORDER_CANCEL")
        assertThat(e.targetType).isEqualTo("ORDER")
        assertThat(e.targetId).isEqualTo("100")
        assertThat(e.ip).isEqualTo("203.0.113.7") // X-Forwarded-For 첫 홉
        assertThat(e.traceId).isEqualTo("trace-abc")
        assertThat(e.before).contains("\"status\":\"PAID\"")
        assertThat(e.after).contains("\"status\":\"CANCELLED\"")
    }

    @Test
    fun `컨텍스트 부재 시 actor=SYSTEM, role·ip·traceId=null로 degrade`() {
        val slot = slot<AuditLogJpaEntity>()
        every { repository.save(capture(slot)) } answers { slot.captured }

        sut.record(AuditAction.PAYMENT_REFUND, "PAYMENT", "100", before = null, after = null)

        val e = slot.captured
        assertThat(e.actor).isEqualTo("SYSTEM")
        assertThat(e.role).isNull()
        assertThat(e.ip).isNull()
        assertThat(e.traceId).isNull()
        assertThat(e.before).isNull()
        assertThat(e.after).isNull()
    }

    @Test
    fun `비-Long principal(익명)은 SYSTEM으로 degrade`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("anonymousUser", null, listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        val slot = slot<AuditLogJpaEntity>()
        every { repository.save(capture(slot)) } answers { slot.captured }

        sut.record(AuditAction.DISPATCH_ASSIGN, "DISPATCH", "1", before = null, after = null)

        assertThat(slot.captured.actor).isEqualTo("SYSTEM")
        assertThat(slot.captured.role).isNull()
    }

    @Test
    fun `before·after 직렬화 실패는 throw 없이 null로 degrade`() {
        val slot = slot<AuditLogJpaEntity>()
        every { repository.save(capture(slot)) } answers { slot.captured }
        val circular = mutableMapOf<String, Any>()
        circular["self"] = circular // Jackson 무한 재귀 → 직렬화 예외

        sut.record(AuditAction.ORDER_CANCEL, "ORDER", "1", before = circular, after = null)

        assertThat(slot.captured.before).isNull()
    }

    @Test
    fun `과길이 targetType·targetId는 컬럼 한도(64)로 truncate`() {
        val slot = slot<AuditLogJpaEntity>()
        every { repository.save(capture(slot)) } answers { slot.captured }
        val longType = "T".repeat(100)

        sut.record(AuditAction.ORDER_CANCEL, longType, "X".repeat(100), before = null, after = null)

        assertThat(slot.captured.targetType).hasSize(64)
        assertThat(slot.captured.targetId).hasSize(64)
    }
}
