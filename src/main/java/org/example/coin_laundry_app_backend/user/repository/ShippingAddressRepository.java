package org.example.coin_laundry_app_backend.user.repository;

import org.example.coin_laundry_app_backend.user.domain.model.entity.ShippingAddress;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ShippingAddressRepository extends
    ReactiveCrudRepository<ShippingAddress, Long>, ShippingAddressCustomRepository {

    Flux<ShippingAddress> findByUserId(@NonNull Long userId);

    Mono<ShippingAddress> findByIdAndUserId(@NonNull Long id, @NonNull Long userId);

    Mono<Long> countByUserId(Long userId);
}
