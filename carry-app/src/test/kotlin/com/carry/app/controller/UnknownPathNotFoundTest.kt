package com.carry.app.controller

import com.carry.app.test.MethodSecurityTestConfig
import com.carry.dispatch.adapter.inbound.rest.DispatchCarrierController
import com.carry.dispatch.application.port.inbound.DispatchCommandUseCase
import com.carry.dispatch.application.port.inbound.DispatchQueryUseCase
import com.carry.security.config.SecurityConfig
import com.carry.security.filter.JwtAuthenticationFilter
import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * 매핑되지 않은 경로는 **404** 여야 한다.
 *
 * 핸들러가 없으면 `NoResourceFoundException` 이 catch-all 로 떨어져 500 + error 로그가 되는데,
 * [13-logging-policy](../../../../../../../docs/13-logging-policy.md) 상 5xx 는 알럿 대상이라
 * 바깥에서 아무 경로나 긁으면 알럿이 울리는 구조가 된다(2026-09-18 라이브 스모크에서 발견:
 * `/api/v2/carrier/dispatches/available` 오타 호출이 500 으로 반환됐다). 서버는 멀쩡하므로 404 가 맞다.
 */
@ActiveProfiles("test")
@WebMvcTest(
    controllers = [DispatchCarrierController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [SecurityConfig::class, JwtAuthenticationFilter::class],
        ),
    ],
)
@Import(MethodSecurityTestConfig::class)
class UnknownPathNotFoundTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var dispatchCommandUseCase: DispatchCommandUseCase

    @MockkBean
    lateinit var dispatchQueryUseCase: DispatchQueryUseCase

    private fun carrierAuth() = authentication(
        UsernamePasswordAuthenticationToken(1L, null, listOf(SimpleGrantedAuthority("ROLE_CARRIER"))),
    )

    @Test
    fun `매핑되지 않은 경로는 404 로 응답한다`() {
        mockMvc.get("/api/v2/no-such-endpoint") { with(carrierAuth()) }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }

    @Test
    fun `존재하는 prefix 아래의 오타 경로도 404 다`() {
        // 실제로 밟았던 경로 — carrier 라는 세그먼트가 없다.
        mockMvc.get("/api/v2/carrier/dispatches/available") { with(carrierAuth()) }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }
}
