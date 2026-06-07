package com.carry.security.handler

import com.carry.common.exception.ErrorCode
import com.carry.common.response.ApiResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/**
 * 미인증(토큰 없음/위조) 요청을 Spring 기본(빈 바디)이 아니라 ApiResponse 봉투(401 UNAUTHORIZED)로 응답한다.
 * 인증 필터 단계라 GlobalExceptionHandler를 거치지 않으므로 여기서 직접 직렬화한다.
 */
@Component
class CarryAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val error = ApiResponse.error(
            ErrorCode.UNAUTHORIZED.status,
            ErrorCode.UNAUTHORIZED.name,
            ErrorCode.UNAUTHORIZED.message,
            MDC.get("traceId"),
        )
        response.status = ErrorCode.UNAUTHORIZED.status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.writer.write(objectMapper.writeValueAsString(error))
    }
}
