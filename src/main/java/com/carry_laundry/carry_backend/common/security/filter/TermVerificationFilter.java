package com.carry_laundry.carry_backend.common.security.filter;

import static com.carry_laundry.carry_backend.common.security.filter.enums.FilterConstant.EXCEPTION;
import static com.carry_laundry.carry_backend.common.security.filter.enums.FilterConstant.TOKEN_DETAIL;

import com.carry_laundry.carry_backend.common.security.payload.TokenDetail;
import com.carry_laundry.carry_backend.term.repository.TermInMemoryCache;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public record TermVerificationFilter(@NonNull TermInMemoryCache termInMemoryCache) implements
    WebFilter {

    @NonNull
    @Override
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        TokenDetail tokenDetail = exchange.getAttribute(TOKEN_DETAIL.getValue());
        var mandatoryTerms = termInMemoryCache.getMandatoryTerms();
        return Mono.fromRunnable(() -> {
                Objects.requireNonNull(tokenDetail).acceptedTerms().forEach((code, termId) ->
                    Optional.ofNullable(mandatoryTerms.get(code))
                        .ifPresentOrElse(termDetail -> {
                            if (!termDetail.id().equals(termId)) {
                                throw new IllegalArgumentException("최신 약관 갱신이 필요합니다: " + code);
                            }
                            mandatoryTerms.remove(code);
                        }, () -> {
                            throw new IllegalArgumentException("약관 동의가 필요합니다:  " + code);
                        })
                );
                if (!mandatoryTerms.isEmpty()) {
                    throw new IllegalArgumentException(
                        "약관 동의가 필요합니다. 약관 코드: " + mandatoryTerms.keySet());
                }
            }).then(chain.filter(exchange))
            .onErrorResume(e -> handleException(exchange, chain, e));
    }

    private Mono<Void> handleException(ServerWebExchange exchange, WebFilterChain chain,
        Throwable e) {
        if (exchange.getAttribute(EXCEPTION.getValue()) == null) {
            exchange.getAttributes().put(EXCEPTION.getValue(),
                new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, e.getMessage()));
        }
        return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.clearContext());
    }
}
