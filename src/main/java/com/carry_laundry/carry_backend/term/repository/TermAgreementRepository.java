package com.carry_laundry.carry_backend.term.repository;

import com.carry_laundry.carry_backend.term.domain.entity.TermAgreement;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TermAgreementRepository extends ReactiveCrudRepository<TermAgreement, Long> {

    Mono<TermAgreement> findByUserIdAndTermId(@NonNull Long userId, @NonNull Long termId);

    Mono<Boolean> existsByUserIdAndTermId(@NonNull Long userId, @NonNull Long termId);

    Flux<TermAgreement> findAllByUserId(Long userId);
}
