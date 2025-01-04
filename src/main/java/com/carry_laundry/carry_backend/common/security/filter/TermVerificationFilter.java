package com.carry_laundry.carry_backend.common.security.filter;

import com.carry_laundry.carry_backend.config.security.jwt.TokenDetail;
import com.carry_laundry.carry_backend.term.repository.TermInMemoryCache;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class TermVerificationFilter implements WebFilter {

    protected static final String TOKEN_DETAIL = "tokenDetail";
    private final TermInMemoryCache termInMemoryCache;

    @NonNull
    @Override
    // TODO: 401로 처리할지 어떻게 처리할지 고민
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        TokenDetail tokenDetail = exchange.getAttribute(TOKEN_DETAIL);
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
            })
            .onErrorResume(e -> {
                    exchange.getAttributes().put("exception", e);
                    return Mono.empty();
                }
            )
            .then(chain.filter(exchange));
    }
}
