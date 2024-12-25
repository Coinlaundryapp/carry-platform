package com.carry_laundry.carry_backend.term.repository;

import com.carry_laundry.carry_backend.term.domain.entity.TermMeta;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface TermMetaRepository extends ReactiveCrudRepository<TermMeta, Long> {

    @Override
    @NonNull
    @Query("""
                INSERT INTO term_metas (title, code, term_type)
                VALUES (:#{#entity.title}, :#{#entity.code}, :#{#entity.termType}::term_types)
                RETURNING *
        """)
    <S extends TermMeta> Mono<S> save(@NonNull S entity);
}
