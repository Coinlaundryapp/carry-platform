package com.carry_laundry.carry_backend.user.repository;

import com.carry_laundry.carry_backend.user.domain.model.entity.data.ShippingAddressData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ShippingAddressRepository extends ReactiveCrudRepository<ShippingAddressData, Long> {

    Flux<ShippingAddressData> findByUserId(@NonNull Long userId);
    Mono<ShippingAddressData> findByIdAndUserId(@NonNull Long id, @NonNull Long userId);
    Mono<Long> countByUserId(Long userId);
}
