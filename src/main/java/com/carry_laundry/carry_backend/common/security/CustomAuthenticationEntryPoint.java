package com.carry_laundry.carry_backend.common.security;

import static com.carry_laundry.carry_backend.common.security.filter.enums.FilterConstant.EXCEPTION;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class CustomAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        ResponseStatusException responseStatusException = exchange.getAttribute(
            EXCEPTION.getValue());
        if (responseStatusException == null) {
            responseStatusException = new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                ex.getMessage());
        }
        var response = exchange.getResponse();
        response.setStatusCode(responseStatusException.getStatusCode());
        if (responseStatusException.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
            response.getHeaders().add(HttpHeaders.WWW_AUTHENTICATE,
                "Bearer realm=\"carrylaundry.com\" error=\"invalid_token\"");
        }
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_VALUE);
        var bufferFactory = response.bufferFactory();
        return response.writeWith(Mono.just(
            bufferFactory.wrap(String.format("""
                    {
                        "code": %d,
                        "message": "%s"
                    }
                    """, responseStatusException.getStatusCode().value(),
                responseStatusException.getReason()).getBytes(StandardCharsets.UTF_8))
        ));

    }
}
