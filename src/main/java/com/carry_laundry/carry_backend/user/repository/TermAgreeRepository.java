package com.carry_laundry.carry_backend.user.repository;

import com.carry_laundry.carry_backend.user.domain.entity.data.TermAgreeData;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TermAgreeRepository extends ReactiveCrudRepository<TermAgreeData, Long> {

    Mono<TermAgreeData> findByUserIdAndTermId(@NonNull Long userId, @NonNull Long termId);

    @Query("SELECT * FROM term_agrees WHERE user_id = :userId")
    Flux<TermAgreeData> findByUserId(@NonNull Long userId);
}
