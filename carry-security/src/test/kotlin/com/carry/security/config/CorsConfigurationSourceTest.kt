package com.carry.security.config

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

/**
 * SecurityConfig 의 CORS 정책 회귀 검증.
 * 프론트(customer/carrier/coordinator-web)가 브라우저에서 직접 백엔드를 호출하므로
 * 로컬 dev/e2e의 여러 포트(localhost:*)는 허용하고, 외부 origin은 거부해야 한다.
 * 모듈화 과정에서 CORS 설정이 누락되면 모든 cross-origin 프리플라이트가 막히는 회귀를 잡는다.
 */
class CorsConfigurationSourceTest {

    private val source =
        SecurityConfig(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
            .corsConfigurationSource()

    private fun allowedOriginFor(origin: String): String? {
        val request = MockHttpServletRequest("OPTIONS", "/api/v2/auth/dev-login")
        request.addHeader("Origin", origin)
        return source.getCorsConfiguration(request)?.checkOrigin(origin)
    }

    @Test
    fun `로컬 프론트 포트(localhost 임의 포트)는 허용된다`() {
        assertThat(allowedOriginFor("http://localhost:3000")).isEqualTo("http://localhost:3000")
        assertThat(allowedOriginFor("http://localhost:3001")).isEqualTo("http://localhost:3001")
        assertThat(allowedOriginFor("http://localhost:3002")).isEqualTo("http://localhost:3002")
    }

    @Test
    fun `운영 origin은 허용된다`() {
        assertThat(allowedOriginFor("https://www.carrylaundry.com"))
            .isEqualTo("https://www.carrylaundry.com")
    }

    @Test
    fun `허용 목록 밖 origin은 거부된다`() {
        assertThat(allowedOriginFor("https://evil.example.com")).isNull()
        assertThat(allowedOriginFor("http://localhost.evil.com:3001")).isNull()
    }

    @Test
    fun `credentials를 허용한다`() {
        val request = MockHttpServletRequest("OPTIONS", "/api/v2/auth/dev-login")
        request.addHeader("Origin", "http://localhost:3001")
        assertThat(source.getCorsConfiguration(request)?.allowCredentials).isTrue()
    }
}
