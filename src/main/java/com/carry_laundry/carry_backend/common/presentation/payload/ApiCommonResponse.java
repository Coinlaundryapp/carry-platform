package com.carry_laundry.carry_backend.common.presentation.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "공통 응답")
public class ApiCommonResponse<T> {

    @Schema(description = "응답 상태 코드", example = "200")
    private int statusCode;
    @Schema(description = "응답 메시지", example = "Success")
    private String message;
    @Schema(description = "응답 데이터")
    private T data;

    private ApiCommonResponse(int status, String message, T data) {
        this.statusCode = status;
        this.message = message;
        this.data = data;
    }

    private ApiCommonResponse(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    private ApiCommonResponse(int statusCode) {
        this.statusCode = statusCode;
    }

    public static <T> ApiCommonResponse<T> createApiResponse(HttpStatus httpStatus, String message,
        T data) {
        return new ApiCommonResponse<>(httpStatus.value(), message, data);
    }

    public static ApiCommonResponse<Void> createFailResponse(HttpStatus httpStatus,
        String message) {
        return new ApiCommonResponse<>(httpStatus.value(), message);
    }

    public static ApiCommonResponse<Void> createSuccessResponse() {
        return new ApiCommonResponse<>(HttpStatus.OK.value());
    }

    public static <T> ApiCommonResponse<T> createSuccessResponse(T data) {
        return new ApiCommonResponse<>(HttpStatus.OK.value(), "Success", data);
    }
}
