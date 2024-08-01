package org.example.coin_laundry_app_backend.user.repository;

import org.example.coin_laundry_app_backend.user.domain.model.entity.data.TermData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface TermRepository extends ReactiveCrudRepository<TermData, Long> {

    Flux<TermData> findByTermInfoTitleOrderByTermInfoVersionDesc(@NonNull String termInfoTitle);
}
