package com.carry.security.handler

import com.carry.common.exception.ErrorCode
import com.carry.common.response.ApiResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/**
 * 필터 단계의 인가 거부(AccessDeniedException)를 ApiResponse 봉투(403 FORBIDDEN)로 응답한다.
 * (메서드 시큐리티 @PreAuthorize 거부는 컨트롤러 호출 중 발생해 GlobalExceptionHandler가 이미 403 봉투로 매핑한다.)
 */
@Component
class CarryAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        val error = ApiResponse.error(
            ErrorCode.FORBIDDEN.status,
            ErrorCode.FORBIDDEN.name,
            ErrorCode.FORBIDDEN.message,
            MDC.get("traceId"),
        )
        response.status = ErrorCode.FORBIDDEN.status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.writer.write(objectMapper.writeValueAsString(error))
    }
}
