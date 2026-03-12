package com.carry_laundry.carry_backend.term.application.service;

import com.carry_laundry.carry_backend.term.domain.entity.Term;
import com.carry_laundry.carry_backend.term.domain.entity.TermMeta;
import com.carry_laundry.carry_backend.term.presentation.payload.response.TermCommonResponse;
import com.carry_laundry.carry_backend.term.repository.TermMetaRepository;
import com.carry_laundry.carry_backend.term.repository.TermRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TermService {

    private final TermRepository termRepository;
    private final TermMetaRepository termMetaRepository;

    public Mono<TermCommonResponse> getLastTermByCode(@NonNull String code) {
        return termRepository.findLastByTermMetaCode(code)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Term not found")));
    }

    public Flux<TermMeta> getTermMetas() {
        return termMetaRepository.findAll();
    }

    public Mono<Term> getTermIdByCodeAndVersion(@NonNull String code, @NonNull int versionCount,
        @NonNull LocalDate createdAt) {
        return termRepository.findByCodeAndVersionCountAndCreatedAt(code, versionCount, createdAt)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Term not found")));
    }
}
