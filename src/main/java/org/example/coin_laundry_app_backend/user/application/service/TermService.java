package org.example.coin_laundry_app_backend.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.Term;
import org.example.coin_laundry_app_backend.user.domain.model.value.TermInfo;
import org.example.coin_laundry_app_backend.user.repository.TermRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TermService {

    private final TermRepository termRepository;

    public Mono<Term> addTerm(Term term) {
        return termRepository.save(term.toData()).map(Term::from);
    }

    public Mono<Term> getTermById(Long id) {
        return termRepository.findById(id).map(Term::from);
    }

    public Mono<Term> getTermByTermInfo(TermInfo termInfo) {
        return termRepository.findByTermInfoTitleAndTermInfoVersion(termInfo.getTitle(),
            termInfo.getVersion()).map(Term::from);
    }

    public Flux<Term> getTermsByTitle(String title) {
        return termRepository.findByTermInfoTitleOrderByTermInfoVersionDesc(title).map(Term::from);
    }

    public Flux<Term> getAllTerms() {
        return termRepository.findAll().map(Term::from);
    }

}
