package com.carry_laundry.carry_backend.user.repository;

import com.carry_laundry.carry_backend.user.domain.entity.ShippingAddress;
import com.carry_laundry.carry_backend.user.repository.custom.ShippingAddressCustomRepository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ShippingAddressRepository extends
    ReactiveCrudRepository<ShippingAddress, Long>, ShippingAddressCustomRepository {

    @Override
    @NonNull
    @Query("""
            INSERT INTO shipping_addresses (user_id, is_default_address, address_label, recipient_name, recipient_phone, base_address, detail_address, delivery_notes, entrance_type, entrance_detail, latitude, longitude)
            VALUES (:#{#entity.userId}, :#{#entity.isDefaultAddress}, :#{#entity.addressLabel}, :#{#entity.recipientName}, :#{#entity.recipientPhone}, :#{#entity.baseAddress}, :#{#entity.detailAddress}, :#{#entity.deliveryNotes}, :#{#entity.entranceType}::entrance_types, :#{#entity.entranceDetail}, :#{#entity.latitude}, :#{#entity.longitude})
            RETURNING *
        """)
    <S extends ShippingAddress> Mono<S> save(@NonNull S entity);

    Flux<ShippingAddress> findByUserId(@NonNull Long userId);

    Mono<ShippingAddress> findByIdAndUserId(@NonNull Long id, @NonNull Long userId);

    Mono<Long> countByUserId(Long userId);

    Mono<ShippingAddress> findByUserIdAndIsDefaultAddressTrue(@NonNull Long userId);
}
