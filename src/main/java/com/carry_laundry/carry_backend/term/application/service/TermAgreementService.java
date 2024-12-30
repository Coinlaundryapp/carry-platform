package com.carry_laundry.carry_backend.term.application.service;

import com.carry_laundry.carry_backend.term.domain.entity.TermAgreement;
import com.carry_laundry.carry_backend.term.repository.TermAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TermAgreementService {

    private final TermAgreementRepository termAgreementRepository;

    public Mono<TermAgreement> createTermAgreement(Long userId, Long termId, boolean agreeYn) {
        return termAgreementRepository.existsByUserIdAndTermId(userId, termId)
            .flatMap(exists -> {
                if (Boolean.TRUE.equals(exists)) {
                    return Mono.error(new IllegalArgumentException("이미 동의/비동의한 약관입니다."));
                }
                return termAgreementRepository.save(TermAgreement.of(userId, termId, agreeYn));
            });
    }

    public Mono<TermAgreement> updateTermAgreement(Long userId, Long termId, boolean agreeYn) {
        return termAgreementRepository.findByUserIdAndTermId(userId, termId)
            .switchIfEmpty(
                Mono.defer(() -> Mono.error(new IllegalArgumentException("약관 동의 정보가 없습니다."))))
            .flatMap(termAgreement -> {
                termAgreement.updateAgreeYn(agreeYn);
                return termAgreementRepository.save(termAgreement);
            });
    }

}
