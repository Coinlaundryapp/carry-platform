package org.example.coin_laundry_app_backend.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ShippingAddressCustomRepositoryImpl implements ShippingAddressCustomRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    @Override
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
