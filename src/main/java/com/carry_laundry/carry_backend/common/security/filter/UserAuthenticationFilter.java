package com.carry_laundry.carry_backend.common.security.filter;

import static com.carry_laundry.carry_backend.common.security.filter.enums.FilterConstant.HEADER_X_USER_ID;
import static com.carry_laundry.carry_backend.common.security.filter.enums.FilterConstant.TOKEN_DETAIL;

import com.carry_laundry.carry_backend.config.security.jwt.JWTAuthenticationToken;
import com.carry_laundry.carry_backend.config.security.jwt.TokenDetail;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public record UserAuthenticationFilter() implements WebFilter {

    private static final List<GrantedAuthority> DEFAULT_AUTHORITIES = List.of(
        new SimpleGrantedAuthority("ROLE_USER"));

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        TokenDetail tokenDetail = exchange.getAttribute(TOKEN_DETAIL.getValue());
        return Mono.defer(() -> {
                if (tokenDetail == null) {
                    return Mono.error(new IllegalArgumentException("토큰이 유효하지 않습니다."));
                }
                long userId = tokenDetail.userId();
                ServerHttpResponse response = exchange.getResponse();
                response.getHeaders()
                    .add(HEADER_X_USER_ID.getValue(), String.valueOf(userId));
                Authentication authentication = new JWTAuthenticationToken(userId, DEFAULT_AUTHORITIES);
                return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
            })
            .onErrorResume(e -> handleException(exchange, e));
    }

    private Mono<Void> handleException(ServerWebExchange exchange, Throwable e) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add(HttpHeaders.WWW_AUTHENTICATE,
            "Bearer realm=\"carrylaundry.com\" error=\"invalid_token\" error_description=\""
                + e.getMessage() + "\"");
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }
}
