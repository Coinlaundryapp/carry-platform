package com.carry.common.exception

import com.carry.common.response.ApiResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 비즈니스 예외 로그 레벨 정책 (ROADMAP Phase 3.3):
     *  - **5xx**(예: `PG_GATEWAY_UNAVAILABLE 503`, `GEOCODING_UNAVAILABLE 503`): `error` — 실제 인프라 장애.
     *  - **4xx**(예: `ORDER_NOT_FOUND 404`, `INVOICE_ALREADY_PAID 409`): `info` — 정상적인 비즈니스 조건이며
     *    오류 알럿이 울려서는 안 된다. 트래픽 패턴 분석 용도로만 노출.
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        val status = e.errorCode.status
        if (status >= 500) {
            log.error("Business exception (5xx): [{}] {}", e.errorCode.name, e.message)
        } else {
            log.info("Business exception ({}): [{}] {}", status, e.errorCode.name, e.message)
        }
        return ResponseEntity
            .status(status)
            .body(ApiResponse.error(status, e.errorCode.name, e.message, MDC.get("traceId")))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val errors = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation failed: {}", errors)
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(400, ErrorCode.INVALID_INPUT.name, errors, MDC.get("traceId")))
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(e: MissingServletRequestParameterException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Missing parameter: {}", e.parameterName)
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(400, ErrorCode.INVALID_INPUT.name, "Missing required parameter: ${e.parameterName}", MDC.get("traceId")))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Type mismatch: {} for parameter {}", e.value, e.name)
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(400, ErrorCode.INVALID_INPUT.name, "Invalid value '${e.value}' for parameter '${e.name}'", MDC.get("traceId")))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Message not readable: {}", e.message)
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(400, ErrorCode.INVALID_INPUT.name, "Malformed request body", MDC.get("traceId")))
    }

    /**
     * JPA `@Version` 기반 optimistic locking이 동시 수정 충돌을 감지하면 던지는 예외.
     * 클라이언트는 같은 요청을 재시도하면 일반적으로 해소된다.
     */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLocking(e: OptimisticLockingFailureException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Optimistic locking conflict: {}", e.message)
        return ResponseEntity
            .status(ErrorCode.CONCURRENT_MODIFICATION.status)
            .body(
                ApiResponse.error(
                    ErrorCode.CONCURRENT_MODIFICATION.status,
                    ErrorCode.CONCURRENT_MODIFICATION.name,
                    ErrorCode.CONCURRENT_MODIFICATION.message,
                    MDC.get("traceId"),
                ),
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unexpected exception", e)
        return ResponseEntity
            .status(500)
            .body(ApiResponse.error(500, ErrorCode.INTERNAL_ERROR.name, "Internal server error", MDC.get("traceId")))
    }
}
