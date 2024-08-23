package org.example.coin_laundry_app_backend.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.TermAgree;
import org.example.coin_laundry_app_backend.user.repository.TermAgreeRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TermAgreeService {

    private final TermAgreeRepository termAgreeRepository;

    public Mono<TermAgree> addTermAgree(TermAgree termAgree) {
        return termAgreeRepository.save(termAgree.toData()).map(TermAgree::from);
    }

    public Flux<TermAgree> getTermAgreesByUserId(Long userId) {
        return termAgreeRepository.findByUserId(userId).map(TermAgree::from);
    }

    public Mono<TermAgree> getTermAgreeByUserIdAndTermId(Long userId, Long termId) {
        return termAgreeRepository.findByUserIdAndTermId(userId, termId).map(TermAgree::from);
    }

    public Mono<TermAgree> updateTermAgree(TermAgree termAgree) {
        return termAgreeRepository.save(termAgree.toData()).map(TermAgree::from);
    }

}
