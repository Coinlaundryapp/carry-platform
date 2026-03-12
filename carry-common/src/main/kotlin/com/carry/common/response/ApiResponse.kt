package com.carry.common.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val status: Int,
    val code: String,
    val message: String,
    val data: T? = null
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> =
            ApiResponse(status = 200, code = "SUCCESS", message = "Success", data = data)

        fun <T> created(data: T): ApiResponse<T> =
            ApiResponse(status = 201, code = "CREATED", message = "Created", data = data)

        fun error(status: Int, code: String, message: String): ApiResponse<Nothing> =
            ApiResponse(status = status, code = code, message = message)
    }
}
