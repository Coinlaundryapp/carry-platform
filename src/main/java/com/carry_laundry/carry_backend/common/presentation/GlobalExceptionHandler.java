package com.carry_laundry.carry_backend.common.presentation;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
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

    @ExceptionHandler(value = HttpClientErrorException.class)
    protected Mono<ApiCommonResponse<Void>> handleHttpClientErrorException(
        HttpClientErrorException e) {
        return Mono.just(
            ApiCommonResponse.createFailResponse(
                Objects.requireNonNull(HttpStatus.resolve(e.getStatusCode().value())),
                e.getMessage()));
    }

    @ExceptionHandler(value = Exception.class)
    protected Mono<ApiCommonResponse<Void>> handleException(Exception e) {
        return Mono.just(
            ApiCommonResponse.createFailResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
    }
}
