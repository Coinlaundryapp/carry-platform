package org.example.coin_laundry_app_backend.user.repository;

import org.example.coin_laundry_app_backend.user.domain.model.entity.data.TermAgreeData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TermAgreeRepository extends ReactiveCrudRepository<TermAgreeData, Long> {

    Flux<TermAgreeData> findByUser_Id(@NonNull Long id);

    Mono<TermAgreeData> findByUser_IdAndTerm_Id(@NonNull Long userId, @NonNull Long termId);
}
