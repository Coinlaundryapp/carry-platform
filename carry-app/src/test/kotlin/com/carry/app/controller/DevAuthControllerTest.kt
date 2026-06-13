package com.carry.app.controller

import com.carry.security.config.SecurityConfig
import com.carry.security.filter.JwtAuthenticationFilter
import com.carry.user.adapter.inbound.rest.DevAuthController
import com.carry.user.application.port.inbound.AuthUseCase
import com.carry.user.application.port.inbound.TokenPair
import com.carry.user.domain.vo.UserRole
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * dev-login 슬라이스 테스트. `@Profile("local | dev")`이 매칭되도록 **local 프로파일**로 컨텍스트를 띄운다
 * (application-local.yml은 env 의존이 없어 슬라이스에서 안전). 실제 토큰의 보호 엔드포인트 동작은
 * 라이브 스모크(curl)로 검증한다 — dev 프로파일 풀컨텍스트는 16개 env를 요구해 통합 테스트에 부적합.
 */
@ActiveProfiles("local")
@WebMvcTest(
    controllers = [DevAuthController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, JwtAuthenticationFilter::class]),
    ],
)
@AutoConfigureMockMvc(addFilters = false)
class DevAuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var authUseCase: AuthUseCase

    @Test
    fun `유효한 역할이면 200과 토큰을 반환하고 그 역할로 devLogin을 호출한다`() {
        every { authUseCase.devLogin(UserRole.CARRIER) } returns TokenPair("acc", "ref")

        mockMvc.post("/api/v2/auth/dev-login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"role": "CARRIER"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { value("acc") }
            jsonPath("$.data.refreshToken") { value("ref") }
        }

        verify(exactly = 1) { authUseCase.devLogin(UserRole.CARRIER) }
    }

    @Test
    fun `소문자 역할도 대문자로 정규화해 수용한다`() {
        every { authUseCase.devLogin(UserRole.COORDINATOR) } returns TokenPair("a", "r")

        mockMvc.post("/api/v2/auth/dev-login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"role": "coordinator"}"""
        }.andExpect {
            status { isOk() }
        }

        verify(exactly = 1) { authUseCase.devLogin(UserRole.COORDINATOR) }
    }

    @Test
    fun `알 수 없는 역할은 400을 반환하고 devLogin을 호출하지 않는다`() {
        mockMvc.post("/api/v2/auth/dev-login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"role": "SUPERADMIN"}"""
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { authUseCase.devLogin(any()) }
    }
}
