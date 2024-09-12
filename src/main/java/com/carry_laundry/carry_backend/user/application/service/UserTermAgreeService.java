package com.carry_laundry.carry_backend.user.application.service;

import com.carry_laundry.carry_backend.user.domain.model.entity.domainmodel.TermAgree;
import com.carry_laundry.carry_backend.user.presentation.payload.response.UserTermAgreeResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserTermAgreeService {

    private final TermAgreeService termAgreeService;

    public Mono<UserTermAgreeResponse> agreeTerm(Long userId, Long termId) {
        LocalDateTime currentTime = LocalDateTime.now();
        return termAgreeService.getTermAgreeByUserIdAndTermId(userId, termId)
            .flatMap(termAgree -> {
                if (Boolean.TRUE.equals(termAgree.getAgreeYn())) {
                    return Mono.defer(() -> {
                        log.warn("이미 동의한 약관입니다: User ID: {}, Term ID: {}", userId, termId);
                        return Mono.error(new IllegalArgumentException(
                            "이미 동의한 약관입니다: User ID: %s, Term ID: %s".formatted(userId, termId)));
                    });
                }
                termAgree.updateAgreeYn(true, currentTime);
                return termAgreeService.updateTermAgree(termAgree);
            }).switchIfEmpty(Mono.defer(() -> termAgreeService.addTermAgree(
                TermAgree.of(userId, termId, true, currentTime))))
            .map(UserTermAgreeResponse::from);
    }

    public Mono<UserTermAgreeResponse> disagreeTerm(Long userId, Long termId) {
        LocalDateTime currentTime = LocalDateTime.now();
        return termAgreeService.getTermAgreeByUserIdAndTermId(userId, termId).flatMap(termAgree -> {
            if (Boolean.FALSE.equals(termAgree.getAgreeYn())) {
                return Mono.defer(() -> {
                    log.warn("이미 철회한 약관입니다: User ID: {}, Term ID: {}", userId, termId);
                    return Mono.error(new IllegalArgumentException(
                        "이미 철회한 약관입니다: User ID: %s, Term ID: %s".formatted(userId, termId)));
                });
            }
            termAgree.updateAgreeYn(false, currentTime);
            return termAgreeService.updateTermAgree(termAgree);
        }).switchIfEmpty(
            Mono.defer(() -> {
                log.warn("약관 동의 내역이 없습니다: User ID: {}, Term ID: {}", userId, termId);
                return Mono.error(new IllegalArgumentException(
                    "약관 동의 내역이 없습니다: User ID: %s, Term ID: %s".formatted(userId, termId)));
            })
        ).map(UserTermAgreeResponse::from);
    }

    public Flux<UserTermAgreeResponse> getTermAgrees(Long userId) {
        return termAgreeService.getTermAgreesByUserId(userId).map(UserTermAgreeResponse::from);
    }
}
