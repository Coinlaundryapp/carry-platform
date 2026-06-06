package com.carry.app.controller

import com.carry.app.test.MethodSecurityTestConfig
import com.carry.dispatch.adapter.inbound.rest.DispatchCoordinatorController
import com.carry.dispatch.application.port.inbound.CarrierAreaUseCase
import com.carry.dispatch.application.port.inbound.DispatchCommandUseCase
import com.carry.dispatch.application.port.inbound.DispatchQueryUseCase
import com.carry.operation.adapter.inbound.rest.OperationDashboardController
import com.carry.operation.adapter.inbound.rest.TermAdminController
import com.carry.operation.application.port.inbound.OperationQueryUseCase
import com.carry.operation.application.port.inbound.TermCommandUseCase
import com.carry.security.config.SecurityConfig
import com.carry.security.filter.JwtAuthenticationFilter
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Nested
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * 기존 코디·어드민 컨트롤러의 role 가드(@PreAuthorize) 회귀 검증.
 * 각 컨트롤러를 올바른 role(통과)·잘못된 role(403) 양방향으로 확인해
 * 가드 누락과 role 문자열 오타를 동시에 잡는다.
 */
@ActiveProfiles("test")
@WebMvcTest(
    controllers = [
        DispatchCoordinatorController::class,
        OperationDashboardController::class,
        TermAdminController::class,
    ],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, JwtAuthenticationFilter::class]),
    ],
)
@Import(MethodSecurityTestConfig::class)
class RoleGuardTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var dispatchCommandUseCase: DispatchCommandUseCase

    @MockkBean
    lateinit var dispatchQueryUseCase: DispatchQueryUseCase

    @MockkBean
    lateinit var carrierAreaUseCase: CarrierAreaUseCase

    @MockkBean
    lateinit var operationQueryUseCase: OperationQueryUseCase

    @MockkBean
    lateinit var termCommandUseCase: TermCommandUseCase

    private fun roleAuth(role: String, userId: Long = 1L) = authentication(
        UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_$role"))),
    )

    @Nested
    inner class DispatchCoordinator {

        @Test
        fun `코디네이터는 배차 취소가 허용된다`() {
            every { dispatchCommandUseCase.cancelDispatch(any()) } returns Unit

            mockMvc.post("/api/v2/coordinator/dispatches/1/cancel") {
                with(roleAuth("COORDINATOR"))
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "운영 취소"}"""
            }.andExpect {
                status { isNoContent() }
            }
        }

        @Test
        fun `코디네이터가 아니면 403을 반환한다`() {
            mockMvc.post("/api/v2/coordinator/dispatches/1/cancel") {
                with(roleAuth("CUSTOMER"))
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "운영 취소"}"""
            }.andExpect {
                status { isForbidden() }
            }
        }
    }

    @Nested
    inner class OperationDashboard {

        @Test
        fun `어드민은 운영 이벤트 조회가 허용된다`() {
            every { operationQueryUseCase.getRecentEvents(any()) } returns emptyList()

            mockMvc.get("/api/v2/admin/dashboard/events") {
                with(roleAuth("ADMIN"))
            }.andExpect {
                status { isOk() }
            }
        }

        @Test
        fun `어드민이 아니면 403을 반환한다`() {
            mockMvc.get("/api/v2/admin/dashboard/events") {
                with(roleAuth("COORDINATOR"))
            }.andExpect {
                status { isForbidden() }
            }
        }
    }

    @Nested
    inner class TermAdmin {

        @Test
        fun `어드민은 약관 비활성화가 허용된다`() {
            every { termCommandUseCase.deactivateTerm(any()) } returns Unit

            mockMvc.delete("/api/v2/admin/terms/1") {
                with(roleAuth("ADMIN"))
                with(csrf())
            }.andExpect {
                status { isNoContent() }
            }
        }

        @Test
        fun `어드민이 아니면 403을 반환한다`() {
            mockMvc.delete("/api/v2/admin/terms/1") {
                with(roleAuth("CUSTOMER"))
                with(csrf())
            }.andExpect {
                status { isForbidden() }
            }
        }
    }
}
