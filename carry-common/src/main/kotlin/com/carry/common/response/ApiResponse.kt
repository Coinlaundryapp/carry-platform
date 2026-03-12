package com.carry.common.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val status: Int,
    val message: String,
    val data: T? = null
) {
    companion object {
        fun <T> success(data: T, message: String = "Success"): ApiResponse<T> =
            ApiResponse(status = 200, message = message, data = data)

        fun <T> created(data: T, message: String = "Created"): ApiResponse<T> =
            ApiResponse(status = 201, message = message, data = data)

        fun error(status: Int, message: String): ApiResponse<Nothing> =
            ApiResponse(status = status, message = message)
    }
}
