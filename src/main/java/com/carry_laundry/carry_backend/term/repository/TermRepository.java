package com.carry_laundry.carry_backend.term.repository;

import com.carry_laundry.carry_backend.term.domain.entity.Term;
import java.time.LocalDate;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
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
    Mono<Term> findFirstByTermMetaIdAndCreatedAt(@NonNull Long termMetaId,
        @NonNull LocalDate createdAt);
}
