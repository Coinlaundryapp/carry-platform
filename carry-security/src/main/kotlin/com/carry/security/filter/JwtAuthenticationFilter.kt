package com.carry.security.filter

import com.carry.security.jwt.JwtProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)
        if (token != null) {
            val principal = jwtProvider.parseToken(token)
            if (principal != null) {
                val authentication = UsernamePasswordAuthenticationToken(
                    principal.userId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_${principal.role}"))
                )
                SecurityContextHolder.getContext().authentication = authentication
                MDC.put(MDC_USER_ID, principal.userId.toString())
            }
        }
        try {
            filterChain.doFilter(request, response)
        } finally {
            // 인증 성공 시 채운 userId를 항상 정리(스레드풀 재사용 누수 방지). 미설정 키 remove는 no-op.
            MDC.remove(MDC_USER_ID)
        }
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearer = request.getHeader("Authorization") ?: return null
        return if (bearer.startsWith("Bearer ")) bearer.substring(7) else null
    }

    private companion object {
        const val MDC_USER_ID = "userId"  // logback includeMdcKeyName과 일치
    }
}
