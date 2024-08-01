package org.example.coin_laundry_app_backend.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.Term;
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

    public Flux<Term> findTermsByTitle(String title) {
        return termRepository.findByTermInfoTitleOrderByTermInfoVersionDesc(title).map(Term::from);
    }

    public Flux<Term> findAllTerms() {
        return termRepository.findAll().map(Term::from);
    }

}
