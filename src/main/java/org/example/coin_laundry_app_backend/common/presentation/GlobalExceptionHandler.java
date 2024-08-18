package org.example.coin_laundry_app_backend.common.presentation;

import com.auth0.jwt.exceptions.JWTVerificationException;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = JWTVerificationException.class)
    protected Mono<ApiCommonResponse<Void>> handleJWTVerificationException(
        JWTVerificationException e) {
        return Mono.just(
            ApiCommonResponse.createFailResponse(HttpStatus.UNAUTHORIZED, e.getMessage()));
    }

    @ExceptionHandler(value = IllegalArgumentException.class)
    protected Mono<ApiCommonResponse<Void>> handleIllegalArgumentException(
        IllegalArgumentException e) {
        return Mono.just(
            ApiCommonResponse.createFailResponse(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    @ExceptionHandler(value = Exception.class)
    protected Mono<ApiCommonResponse<Void>> handleException(Exception e) {
        return Mono.just(
            ApiCommonResponse.createFailResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
    }
}
