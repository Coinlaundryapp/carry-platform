package com.carry.app.controller

import com.carry.app.admin.AdminDlqController
import com.carry.app.test.MethodSecurityTestConfig
import com.carry.audit.domain.AuditAction
import com.carry.audit.port.AuditPort
import com.carry.infra.kafka.dlq.DlqPurgeResult
import com.carry.infra.kafka.dlq.DlqPurgeService
import com.carry.infra.kafka.dlq.DlqRedriveResult
import com.carry.infra.kafka.dlq.DlqRedriveService
import com.carry.security.config.SecurityConfig
import com.carry.security.filter.JwtAuthenticationFilter
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@ActiveProfiles("test")
@WebMvcTest(
    controllers = [AdminDlqController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, JwtAuthenticationFilter::class]),
    ],
)
@Import(MethodSecurityTestConfig::class)
class AdminDlqControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var dlqRedriveService: DlqRedriveService

    @MockkBean
    lateinit var dlqPurgeService: DlqPurgeService

    @MockkBean
    lateinit var auditPort: AuditPort

    private fun roleAuth(role: String, userId: Long = 1L) = authentication(
        UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_$role"))),
    )

    @Test
    fun `ADMIN이 redrive를 요청하면 결과를 반환하고 감사 로그를 남긴다`() {
        every { dlqRedriveService.redrive("order.event", 100) } returns DlqRedriveResult(redriven = 2, parked = 1)
        justRun { auditPort.record(any(), any(), any(), any(), any()) }

        mockMvc.post("/api/v2/admin/dlq/redrive") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"topic": "order.event", "maxRecords": 100}"""
            with(roleAuth("ADMIN"))
            with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.redriven") { value(2) }
            jsonPath("$.data.parked") { value(1) }
        }

        verify {
            auditPort.record(
                action = AuditAction.DLQ_REDRIVE,
                targetType = "KafkaTopic",
                targetId = "order.event",
                before = null,
                after = DlqRedriveResult(redriven = 2, parked = 1),
            )
        }
    }

    @Test
    fun `ADMIN이 아니면 403이고 redrive는 실행되지 않는다`() {
        mockMvc.post("/api/v2/admin/dlq/redrive") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"topic": "order.event", "maxRecords": 100}"""
            with(roleAuth("COORDINATOR"))
            with(csrf())
        }.andExpect {
            status { isForbidden() }
        }

        verify(exactly = 0) { dlqRedriveService.redrive(any(), any()) }
    }

    @Test
    fun `ADMIN이 purge를 요청하면 결과를 반환하고 감사 로그를 남긴다`() {
        every { dlqPurgeService.purge("order.event") } returns DlqPurgeResult(purged = 5)
        justRun { auditPort.record(any(), any(), any(), any(), any()) }

        mockMvc.post("/api/v2/admin/dlq/purge") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"topic": "order.event"}"""
            with(roleAuth("ADMIN"))
            with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.purged") { value(5) }
        }

        verify {
            auditPort.record(
                action = AuditAction.DLQ_PURGE,
                targetType = "KafkaTopic",
                targetId = "order.event",
                before = null,
                after = DlqPurgeResult(purged = 5),
            )
        }
    }

    @Test
    fun `ADMIN이 아니면 403이고 purge는 실행되지 않는다`() {
        mockMvc.post("/api/v2/admin/dlq/purge") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"topic": "order.event"}"""
            with(roleAuth("COORDINATOR"))
            with(csrf())
        }.andExpect {
            status { isForbidden() }
        }

        verify(exactly = 0) { dlqPurgeService.purge(any()) }
    }
}
