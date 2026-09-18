package com.carry.security.filter

import com.carry.security.jwt.JwtPrincipal
import com.carry.security.jwt.JwtProvider
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
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
        MDC.clear()
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

    /** filterChain 실행 "도중"의 userId MDC 값을 기록하는 FilterChain. */
    private class CapturingChain(val onInvoke: () -> Unit = {}) : FilterChain {
        var userIdDuringChain: String? = "__not_invoked__"
        override fun doFilter(req: jakarta.servlet.ServletRequest?, res: jakarta.servlet.ServletResponse?) {
            userIdDuringChain = MDC.get("userId")
            onInvoke()
        }
    }

    @Test
    fun `유효한 토큰이면 체인 실행 중 userId가 MDC에 있고 종료 후 정리된다`() {
        every { jwtProvider.parseToken("good-token") } returns JwtPrincipal(99L, "CUSTOMER")
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer good-token") }
        val chain = CapturingChain()

        sut.doFilter(request, MockHttpServletResponse(), chain)

        assertThat(chain.userIdDuringChain).isEqualTo("99")
        assertThat(MDC.get("userId")).isNull()
    }

    @Test
    fun `토큰이 없으면 체인 실행 중에도 종료 후에도 userId MDC가 없다`() {
        val chain = CapturingChain()

        sut.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain)

        assertThat(chain.userIdDuringChain).isNull()
        assertThat(MDC.get("userId")).isNull()
    }

    @Test
    fun `토큰 검증에 실패하면 체인 실행 중에도 종료 후에도 userId MDC가 없다`() {
        every { jwtProvider.parseToken("bad-token") } returns null
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer bad-token") }
        val chain = CapturingChain()

        sut.doFilter(request, MockHttpServletResponse(), chain)

        assertThat(chain.userIdDuringChain).isNull()
        assertThat(MDC.get("userId")).isNull()
    }

    @Test
    fun `체인이 예외를 던져도 userId MDC는 정리된다`() {
        every { jwtProvider.parseToken("good-token") } returns JwtPrincipal(99L, "CUSTOMER")
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer good-token") }
        val chain = CapturingChain(onInvoke = { throw RuntimeException("downstream") })

        assertThatThrownBy { sut.doFilter(request, MockHttpServletResponse(), chain) }
            .isInstanceOf(RuntimeException::class.java)

        assertThat(MDC.get("userId")).isNull()
    }
}
