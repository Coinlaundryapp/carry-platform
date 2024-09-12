package com.carry_laundry.carry_backend.user.repository;

import com.carry_laundry.carry_backend.user.domain.model.entity.data.TermData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TermRepository extends ReactiveCrudRepository<TermData, Long> {

    Flux<TermData> findByTermInfoTitleOrderByTermInfoVersionDesc(@NonNull String termInfoTitle);

    Mono<TermData> findByTermInfoTitleAndTermInfoVersion(@NonNull String termInfoTitle,
        @NonNull Integer termInfoVersion);
}
