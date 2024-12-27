package com.carry_laundry.carry_backend.term.repository;

import com.carry_laundry.carry_backend.term.application.record.TermDetail;
import com.carry_laundry.carry_backend.term.domain.entity.Term;
import java.time.LocalDate;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TermRepository extends ReactiveCrudRepository<Term, Long> {

    @Query("""
        SELECT *
        FROM terms
        WHERE term_meta_id = :termMetaId
        AND created_at = :createdAt
        ORDER BY id DESC
        LIMIT 1
        """)
    Mono<Term> findByTermMetaIdAndCreatedAt(@NonNull Long termMetaId,
        @NonNull LocalDate createdAt);

    @Query("""
        SELECT DISTINCT ON (tm.id)
            t.id, tm.code, tm.term_type, t.version_count, t.created_at
        FROM terms as t
                 LEFT JOIN term_metas as tm ON t.term_meta_id = tm.id
        ORDER BY tm.id, t.created_at DESC, t.version_count DESC
        """)
    Flux<TermDetail> findTermDetailsByLastVersion();
}
