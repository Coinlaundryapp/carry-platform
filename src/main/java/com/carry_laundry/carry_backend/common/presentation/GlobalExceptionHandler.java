package com.carry_laundry.carry_backend.common.presentation;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(value = JWTVerificationException.class)
    protected Mono<ApiCommonResponse<Void>> handleJWTVerificationException(
        JWTVerificationException e) {
        return Mono.just(
            ApiCommonResponse.createFailResponse(HttpStatus.UNAUTHORIZED, e.getMessage()));
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = IllegalArgumentException.class)
    protected Mono<ApiCommonResponse<Void>> handleIllegalArgumentException(
        IllegalArgumentException e) {
        return Mono.just(
            ApiCommonResponse.createFailResponse(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    @ExceptionHandler(value = HttpClientErrorException.class)
    protected Mono<ResponseEntity<ApiCommonResponse<Void>>> handleHttpClientErrorException(
        HttpClientErrorException e) {
        return Mono.just(
            ResponseEntity.status(e.getStatusCode())
                .body(ApiCommonResponse.createFailResponse(
                    Objects.requireNonNull(HttpStatus.resolve(e.getStatusCode().value())),
                    e.getMessage()))
        );
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(value = Exception.class)
    protected Mono<ApiCommonResponse<Void>> handleException(Exception e) {
        log.error(e.getMessage());
        return Mono.just(
            ApiCommonResponse.createFailResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
    }
}
