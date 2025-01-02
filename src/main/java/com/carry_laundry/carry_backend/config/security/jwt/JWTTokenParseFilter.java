package com.carry_laundry.carry_backend.config.security.jwt;

import com.auth0.jwt.exceptions.JWTVerificationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class JWTTokenParseFilter implements WebFilter {

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String EXCEPTION = "exception";

    private final JWTHelper jwtHelper;

    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            return handleException(exchange, chain, new JWTVerificationException("토큰이 없습니다."));
        }
        String extractedToken = token.substring(TOKEN_PREFIX.length());
        return Mono.fromCallable(() -> jwtHelper.parse(extractedToken))
            .flatMap(tokenDetail -> {
                long userId = tokenDetail.userId();
                exchange.getAttributes().put("tokenDetail", tokenDetail);
                exchange.getResponse().getHeaders().add("X-USER-ID", String.valueOf(userId));
                JWTAuthenticationToken authentication = new JWTAuthenticationToken(
                    userId, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
            })
            .onErrorResume(e -> handleException(exchange, chain, e));
    }

    private Mono<Void> handleException(ServerWebExchange exchange, WebFilterChain chain,
        Throwable e) {
        exchange.getAttributes().put(EXCEPTION, e);
        return chain.filter(exchange);
    }
}
