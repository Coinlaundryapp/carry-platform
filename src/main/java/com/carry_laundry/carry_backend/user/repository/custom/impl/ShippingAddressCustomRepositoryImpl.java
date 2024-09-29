package com.carry_laundry.carry_backend.user.repository.custom.impl;

import com.carry_laundry.carry_backend.user.repository.custom.ShippingAddressCustomRepository;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Mono;

public class ShippingAddressCustomRepositoryImpl implements ShippingAddressCustomRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public ShippingAddressCustomRepositoryImpl(R2dbcEntityTemplate r2dbcEntityTemplate) {
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
    }

    public Mono<Void> updateShippingAddressToDefault(Long userId, Long addressId) {
        String updateQuery = """
            UPDATE shipping_addresses
            SET is_default_address = CASE
                WHEN id = :addressId THEN true
                ELSE false
            END
            WHERE user_id = :userId
            """;
        return r2dbcEntityTemplate.getDatabaseClient().sql(updateQuery)
            .bind("userId", userId)
            .bind("addressId", addressId)
            .then();
    }
}
