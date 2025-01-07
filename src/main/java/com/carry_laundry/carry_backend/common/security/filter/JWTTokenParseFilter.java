package com.carry_laundry.carry_backend.common.security.filter;

import static com.carry_laundry.carry_backend.common.security.filter.enums.FilterConstant.EXCEPTION;
import static com.carry_laundry.carry_backend.common.security.filter.enums.FilterConstant.TOKEN_DETAIL;
import static com.carry_laundry.carry_backend.common.security.filter.enums.FilterConstant.TOKEN_PREFIX;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.carry_laundry.carry_backend.common.security.utils.JWTHelper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public record JWTTokenParseFilter(@NonNull JWTHelper jwtHelper) implements WebFilter {

    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        // 토큰 검증: 토큰이 없거나 Bearer로 시작하지 않으면 예외 처리
        if (token == null || !token.startsWith(TOKEN_PREFIX.getValue())) {
            return handleException(exchange, chain, new JWTVerificationException("토큰이 없습니다."));
        }
        String extractedToken = token.substring(TOKEN_PREFIX.getValue().length());
        return Mono.fromCallable(() -> jwtHelper.parse(extractedToken))
            .flatMap(tokenDetail -> {
                exchange.getAttributes().put(TOKEN_DETAIL.getValue(), tokenDetail);
                return chain.filter(exchange);
            })
            .onErrorResume(e -> handleException(exchange, chain, e));
    }

    private Mono<Void> handleException(ServerWebExchange exchange, WebFilterChain chain,
        Throwable e) {
        exchange.getAttributes().put(EXCEPTION.getValue(), new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, e.getMessage()));
        return chain.filter(exchange);
    }
}
