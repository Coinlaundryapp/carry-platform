package com.carry_laundry.carry_backend.term.application.service;

import com.carry_laundry.carry_backend.term.domain.entity.Term;
import com.carry_laundry.carry_backend.term.domain.entity.TermMeta;
import com.carry_laundry.carry_backend.term.domain.enums.TermType;
import com.carry_laundry.carry_backend.term.repository.TermInMemoryCache;
import com.carry_laundry.carry_backend.term.repository.TermMetaRepository;
import com.carry_laundry.carry_backend.term.repository.TermRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TermAdminService {

    private final TermRepository termRepository;
    private final TermMetaRepository termMetaRepository;
    private final TermInMemoryCache termInMemoryCache;

    public Mono<TermMeta> createTermMeta(String title, String code, String termType) {
        return validateTermMetaInputs(title, code, termType)
            .flatMap(termTypeEnum ->
                termMetaRepository.existsByCodeAndTermType(code, termTypeEnum)
                    .flatMap(exists -> {
                        if (Boolean.TRUE.equals(exists)) {
                            log.error("이미 존재하는 약관 코드입니다: {}", code);
                            return Mono.error(
                                new IllegalArgumentException("이미 존재하는 약관 코드입니다: " + code));
                        }
                        return termMetaRepository.save(TermMeta.of(title, code, termTypeEnum));
                    })
            );
    }

    public Flux<TermMeta> getTermMetas() {
        return termMetaRepository.findAll();
    }

    public Mono<Term> createTerm(Long termMetaId, String content) {
        return validateTermInputs(termMetaId, content).flatMap(termMeta -> {
            LocalDate currentDate = LocalDate.now();
            return termRepository.findByTermMetaIdAndCreatedAt(termMeta.getId(), currentDate)
                .flatMap(term -> {
                    int nextVersion = term.getVersionCount() + 1;
                    return termRepository.save(
                        Term.of(termMeta.getId(), content, nextVersion, currentDate));
                }).switchIfEmpty(Mono.defer(
                    () -> termRepository.save(Term.of(termMeta.getId(), content, 1, currentDate))))
                .doOnNext(term -> termInMemoryCache.updateCache(termMeta, term));
        });
    }

    private Mono<TermMeta> validateTermInputs(Long termMetaId, String content) {
        if (termMetaId == null) {
            return Mono.error(new IllegalArgumentException("termMetaId should not be null"));
        }
        if (content == null || content.isBlank()) {
            return Mono.error(new IllegalArgumentException("content should not be blank"));
        }
        return termMetaRepository.findById(termMetaId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("termMeta not found")));
    }

    private Mono<TermType> validateTermMetaInputs(String title, String code, String termType) {
        if (title == null || title.isBlank()) {
            return Mono.error(new IllegalArgumentException("title should not be blank"));
        }
        if (code == null || code.isBlank()) {
            return Mono.error(new IllegalArgumentException("code should not be blank"));
        }
        if (termType == null || termType.isBlank()) {
            return Mono.error(new IllegalArgumentException("termType should not be blank"));
        }
        try {
            TermType enumType = TermType.valueOf(termType);
            return Mono.just(enumType);
        } catch (IllegalArgumentException e) {
            log.error("Invalid termType: {}", termType);
            return Mono.error(new IllegalArgumentException("Invalid termType: " + termType));
        }
    }
}
