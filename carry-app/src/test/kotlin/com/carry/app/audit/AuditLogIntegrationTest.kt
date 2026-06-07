package com.carry.app.audit

import com.carry.app.test.IntegrationTestBase
import com.carry.audit.adapter.outbound.persistence.AuditLogJpaRepository
import com.carry.audit.domain.AuditAction
import com.carry.audit.port.AuditPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder

/**
 * 감사 로그 풀스택 통합. V19 마이그레이션 적용 + 엔티티 validate + 실 Postgres JSONB
 * before/after 라운드트립 + ambient actor 해석(인증=userId / 부재=SYSTEM)을 실 DB로 검증.
 */
@Transactional
class AuditLogIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var auditPort: AuditPort
    @Autowired private lateinit var repository: AuditLogJpaRepository

    @BeforeEach
    @AfterEach
    fun clear() {
        SecurityContextHolder.clearContext()
        RequestContextHolder.resetRequestAttributes() // saga 경로 시뮬레이션: 요청 컨텍스트 없음
        repository.deleteAll()
    }

    @Test
    fun `인증된 코디 컨텍스트의 감사가 actor·role·JSONB로 영속된다`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(7L, null, listOf(SimpleGrantedAuthority("ROLE_COORDINATOR")))

        auditPort.record(
            AuditAction.ORDER_CANCEL, "ORDER", "100",
            before = mapOf("status" to "PAID"),
            after = mapOf("status" to "REFUND_PENDING", "reason" to "테스트"),
        )

        val rows = repository.findAll()
        assertThat(rows).hasSize(1)
        val row = rows.first()
        assertThat(row.actor).isEqualTo("7")
        assertThat(row.role).isEqualTo("COORDINATOR")
        assertThat(row.action).isEqualTo("ORDER_CANCEL")
        assertThat(row.targetType).isEqualTo("ORDER")
        assertThat(row.targetId).isEqualTo("100")
        assertThat(row.before).contains("PAID")           // JSONB 라운드트립
        assertThat(row.after).contains("REFUND_PENDING")
    }

    @Test
    fun `컨텍스트 없는(saga) 경로는 actor=SYSTEM으로 영속된다`() {
        auditPort.record(AuditAction.PAYMENT_REFUND, "PAYMENT", "100", before = null, after = null)

        val row = repository.findAll().first()
        assertThat(row.actor).isEqualTo("SYSTEM")
        assertThat(row.role).isNull()
        assertThat(row.ip).isNull()
    }
}
