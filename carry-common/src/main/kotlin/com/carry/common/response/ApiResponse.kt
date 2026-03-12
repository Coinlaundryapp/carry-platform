package com.carry.common.response

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "공통 API 응답 래퍼")
data class ApiResponse<T>(
    @Schema(description = "HTTP 상태 코드", example = "200")
    val status: Int,
    @Schema(description = "응답 코드", example = "SUCCESS")
    val code: String,
    @Schema(description = "응답 메시지", example = "Success")
    val message: String,
    @Schema(description = "응답 데이터")
    val data: T? = null,
    @Schema(description = "트레이스 ID (에러 응답 시 포함)")
    val traceId: String? = null
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> =
            ApiResponse(status = 200, code = "SUCCESS", message = "Success", data = data)

        fun <T> created(data: T): ApiResponse<T> =
            ApiResponse(status = 201, code = "CREATED", message = "Created", data = data)

        fun error(status: Int, code: String, message: String, traceId: String? = null): ApiResponse<Nothing> =
            ApiResponse(status = status, code = code, message = message, traceId = traceId)
    }
}
