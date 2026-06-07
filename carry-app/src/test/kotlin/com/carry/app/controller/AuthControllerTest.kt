package com.carry.app.controller

import com.carry.user.application.port.inbound.AuthUseCase
import com.carry.user.application.port.inbound.LoginResult
import com.carry.user.application.port.inbound.Prefill
import com.carry.user.application.port.inbound.TokenPair
import com.carry.user.domain.exception.AuthTokenInvalidException
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
import com.carry.security.config.SecurityConfig
import com.carry.security.filter.JwtAuthenticationFilter
import com.carry.user.adapter.inbound.rest.AuthController

@ActiveProfiles("test")
@WebMvcTest(
    controllers = [AuthController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, JwtAuthenticationFilter::class]),
    ],
)
@AutoConfigureMockMvc(addFilters = false) // /api/v2/auth/** 는 운영에서 permitAll — 슬라이스에선 필터 비활성
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var authUseCase: AuthUseCase

    @Test
    fun `기존 유저 로그인은 200과 토큰을 반환한다`() {
        every { authUseCase.loginWithKakao("kakao-at") } returns
            LoginResult.Registered(TokenPair("acc", "ref"))

        mockMvc.post("/api/v2/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"kakaoAccessToken": "kakao-at"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("REGISTERED") }
            jsonPath("$.data.accessToken") { value("acc") }
            jsonPath("$.data.refreshToken") { value("ref") }
        }
    }

    @Test
    fun `신규 유저 로그인은 200과 가입 토큰을 반환한다`() {
        every { authUseCase.loginWithKakao("kakao-at") } returns
            LoginResult.RegistrationRequired("signup-token", Prefill("new@example.com", "새닉"))

        mockMvc.post("/api/v2/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"kakaoAccessToken": "kakao-at"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("REGISTRATION_REQUIRED") }
            jsonPath("$.data.signupToken") { value("signup-token") }
            jsonPath("$.data.prefill.email") { value("new@example.com") }
        }
    }

    @Test
    fun `로그인 토큰이 비면 400을 반환한다`() {
        mockMvc.post("/api/v2/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"kakaoAccessToken": ""}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `회원가입은 201과 토큰을 반환한다`() {
        every { authUseCase.completeSignup("signup-token", "이름", "01012345678", "a@b.com") } returns
            TokenPair("acc", "ref")

        mockMvc.post("/api/v2/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"signupToken":"signup-token","name":"이름","phone":"01012345678","email":"a@b.com"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.accessToken") { value("acc") }
            jsonPath("$.data.refreshToken") { value("ref") }
        }
        verify { authUseCase.completeSignup("signup-token", "이름", "01012345678", "a@b.com") }
    }

    @Test
    fun `회원가입 필드가 비면 400을 반환한다`() {
        mockMvc.post("/api/v2/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"signupToken":"signup-token","name":"","phone":"01012345678","email":"a@b.com"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `refresh는 200과 회전된 새 access·refresh 토큰을 반환한다`() {
        every { authUseCase.refresh("ref") } returns TokenPair("new-acc", "new-ref")

        mockMvc.post("/api/v2/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken": "ref"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { value("new-acc") }
            jsonPath("$.data.refreshToken") { value("new-ref") }
        }
    }

    @Test
    fun `무효한 refresh 토큰은 401을 반환한다`() {
        every { authUseCase.refresh("bad") } throws AuthTokenInvalidException()

        mockMvc.post("/api/v2/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken": "bad"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_TOKEN_INVALID") }
        }
    }

    @Test
    fun `logout은 204를 반환하고 세션 폐기를 위임한다`() {
        every { authUseCase.logout("ref") } returns Unit

        mockMvc.post("/api/v2/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken": "ref"}"""
        }.andExpect {
            status { isNoContent() }
        }
        verify { authUseCase.logout("ref") }
    }

    @Test
    fun `logout 토큰이 비면 400을 반환한다`() {
        mockMvc.post("/api/v2/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken": ""}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
