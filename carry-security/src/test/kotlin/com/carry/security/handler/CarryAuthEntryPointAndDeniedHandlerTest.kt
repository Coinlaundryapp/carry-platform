package com.carry.security.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.InsufficientAuthenticationException

class CarryAuthEntryPointAndDeniedHandlerTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `미인증 요청에는 401과 UNAUTHORIZED 봉투를 쓴다`() {
        val sut = CarryAuthenticationEntryPoint(objectMapper)
        val response = MockHttpServletResponse()

        sut.commence(MockHttpServletRequest(), response, InsufficientAuthenticationException("unauthenticated"))

        assertThat(response.status).isEqualTo(401)
        assertThat(response.contentType).contains("application/json")
        assertThat(response.contentAsString).contains("\"code\":\"UNAUTHORIZED\"")
        assertThat(response.contentAsString).contains("\"status\":401")
    }

    @Test
    fun `인가 거부에는 403과 FORBIDDEN 봉투를 쓴다`() {
        val sut = CarryAccessDeniedHandler(objectMapper)
        val response = MockHttpServletResponse()

        sut.handle(MockHttpServletRequest(), response, AccessDeniedException("denied"))

        assertThat(response.status).isEqualTo(403)
        assertThat(response.contentType).contains("application/json")
        assertThat(response.contentAsString).contains("\"code\":\"FORBIDDEN\"")
        assertThat(response.contentAsString).contains("\"status\":403")
    }
}
