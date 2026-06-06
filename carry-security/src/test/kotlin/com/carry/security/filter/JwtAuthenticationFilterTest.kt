package com.carry.security.filter

import com.carry.security.jwt.JwtPrincipal
import com.carry.security.jwt.JwtProvider
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class JwtAuthenticationFilterTest {

    private val jwtProvider = mockk<JwtProvider>()
    private val sut = JwtAuthenticationFilter(jwtProvider)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `유효한 토큰의 role을 ROLE_ 접두 권한으로 설정하고 principal은 userId로 둔다`() {
        every { jwtProvider.parseToken("good-token") } returns JwtPrincipal(99L, "COORDINATOR")
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer good-token") }

        sut.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        val authentication = SecurityContextHolder.getContext().authentication
        assertThat(authentication).isNotNull
        assertThat(authentication.principal).isEqualTo(99L)
        assertThat(authentication.authorities.map { it.authority }).containsExactly("ROLE_COORDINATOR")
    }

    @Test
    fun `Authorization 헤더가 없으면 인증을 설정하지 않는다`() {
        sut.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `토큰 검증에 실패하면 인증을 설정하지 않는다`() {
        every { jwtProvider.parseToken("bad-token") } returns null
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer bad-token") }

        sut.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }
}
