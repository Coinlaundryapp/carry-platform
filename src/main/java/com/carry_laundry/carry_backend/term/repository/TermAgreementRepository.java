package com.carry_laundry.carry_backend.term.repository;

import com.carry_laundry.carry_backend.term.application.record.TermAgreementDetail;
import com.carry_laundry.carry_backend.term.domain.entity.TermAgreement;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TermAgreementRepository extends ReactiveCrudRepository<TermAgreement, Long> {

    Mono<TermAgreement> findByUserIdAndTermId(@NonNull Long userId, @NonNull Long termId);

    Mono<Boolean> existsByUserIdAndTermId(@NonNull Long userId, @NonNull Long termId);

    @Query("""
        SELECT DISTINCT ON (tm.id)
            ta.term_id, tm.code, tm.term_type, ta.agree_yn
        FROM term_agreements AS ta
        INNER JOIN terms AS t ON ta.term_id = t.id
        INNER JOIN term_metas AS tm ON t.term_meta_id = tm.id
        WHERE ta.user_id = :userId
        ORDER BY tm.id, t.created_at DESC, t.version_count DESC
        """)
    Flux<TermAgreementDetail> findAllByUserId(Long userId);
}
