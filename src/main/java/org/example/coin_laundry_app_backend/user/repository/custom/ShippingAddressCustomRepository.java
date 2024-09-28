package org.example.coin_laundry_app_backend.user.repository.custom;

import reactor.core.publisher.Mono;

public interface ShippingAddressCustomRepository {
    Mono<Void> updateShippingAddressToDefault(Long userId, Long addressId);
}
