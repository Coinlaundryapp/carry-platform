package com.carry.app.controller

import com.carry.app.test.MethodSecurityTestConfig
import com.carry.order.adapter.inbound.rest.OrderCoordinatorController
import com.carry.order.application.port.inbound.OrderCommandUseCase
import com.carry.security.config.SecurityConfig
import com.carry.security.filter.JwtAuthenticationFilter
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
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
    controllers = [OrderCoordinatorController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, JwtAuthenticationFilter::class]),
    ],
)
@Import(MethodSecurityTestConfig::class)
class OrderCoordinatorControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var orderCommandUseCase: OrderCommandUseCase

    private fun roleAuth(role: String, userId: Long = 1L) = authentication(
        UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_$role"))),
    )

    @Test
    fun `코디네이터가 주문 취소를 요청하면 204를 반환하고 COORDINATOR로 취소한다`() {
        every { orderCommandUseCase.cancelOrder(1L, "운영 취소", "COORDINATOR") } returns Unit

        mockMvc.post("/api/v2/coordinator/orders/1/cancel") {
            with(roleAuth("COORDINATOR"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason": "운영 취소"}"""
        }.andExpect {
            status { isNoContent() }
        }

        verify { orderCommandUseCase.cancelOrder(1L, "운영 취소", "COORDINATOR") }
    }

    @Test
    fun `코디네이터 역할이 아니면 403을 반환하고 취소를 호출하지 않는다`() {
        mockMvc.post("/api/v2/coordinator/orders/1/cancel") {
            with(roleAuth("CUSTOMER"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason": "운영 취소"}"""
        }.andExpect {
            status { isForbidden() }
        }

        verify(exactly = 0) { orderCommandUseCase.cancelOrder(any(), any(), any()) }
    }

    @Test
    fun `인증 없이 접근하면 401을 반환한다`() {
        mockMvc.post("/api/v2/coordinator/orders/1/cancel") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason": "운영 취소"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `취소 사유가 없으면 400을 반환한다`() {
        mockMvc.post("/api/v2/coordinator/orders/1/cancel") {
            with(roleAuth("COORDINATOR"))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason": ""}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
