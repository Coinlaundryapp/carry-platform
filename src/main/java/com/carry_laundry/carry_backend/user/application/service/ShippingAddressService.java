package com.carry_laundry.carry_backend.user.application.service;

import com.carry_laundry.carry_backend.user.application.record.shipping.ShippingSummary;
import com.carry_laundry.carry_backend.user.domain.converter.ShippingAddressConverter;
import com.carry_laundry.carry_backend.user.domain.model.entity.domainmodel.ShippingAddress;
import com.carry_laundry.carry_backend.user.presentation.payload.request.shipping.CreateAddressRequest;
import com.carry_laundry.carry_backend.user.presentation.payload.request.shipping.UpdateAddressRequest;
import com.carry_laundry.carry_backend.user.repository.ShippingAddressRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ShippingAddressService {

    private final DatabaseClient databaseClient;
    private final ShippingAddressRepository shippingAddressRepository;

    @Transactional
    public Mono<List<ShippingSummary>> getAllShippingAddresses(Long userId) {
        return shippingAddressRepository.findByUserId(userId)
            .map(ShippingAddressConverter::toDomain)
            .map(ShippingSummary::of)
            .collectList();
    }

    @Transactional
    public Mono<ShippingAddress> getShippingAddressById(Long userId, Long addressId) {
        return shippingAddressRepository.findByIdAndUserId(addressId, userId)
            .map(ShippingAddressConverter::toDomain);
    }

    @Transactional
    public Mono<ShippingAddress> addShippingAddress(Long userId, CreateAddressRequest request) {
        return shippingAddressRepository.countByUserId(userId)
            .flatMap(count -> {
                ShippingAddress shippingAddress = ShippingAddress.create(
                    userId, request.addressLabel(), request.recipientName(),
                    request.recipientPhone(),
                    request.baseAddress(), request.detailAddress(), request.deliveryNotes(),
                    request.entranceType(), request.entranceDetail()
                );
                if (count == 0) {
                    shippingAddress.markAsDefaultAddress();
                } else {
                    shippingAddress.clearDefaultAddress();
                }
                return shippingAddressRepository.save(
                    ShippingAddressConverter.toData(shippingAddress));
            }).map(ShippingAddressConverter::toDomain);
    }

    @Transactional
    public Mono<ShippingAddress> updateShippingAddress(Long userId, Long addressId,
        UpdateAddressRequest request) {
        return shippingAddressRepository.findByIdAndUserId(addressId, userId)
            .map(ShippingAddressConverter::toDomain)
            .flatMap(existingAddress -> {
                existingAddress.overwrite(request.addressLabel(), request.recipientName(),
                    request.recipientPhone(),
                    request.baseAddress(), request.detailAddress(), request.deliveryNotes(),
                    request.entranceType(), request.entranceDetail());
                return shippingAddressRepository.save(
                    ShippingAddressConverter.toData(existingAddress));
            })
            .map(ShippingAddressConverter::toDomain);
    }

    @Transactional
    public Mono<Void> deleteShippingAddress(Long userId, Long addressId) {
        return shippingAddressRepository.findByIdAndUserId(addressId, userId)
            .map(ShippingAddressConverter::toDomain)
            .flatMap(shippingAddress -> {
                if (shippingAddress.getIsDefaultAddress()) {
                    return Mono.error(
                        new RuntimeException("default shipping-address cannot be deleted"));
                }
                return Mono.just(ShippingAddressConverter.toData(shippingAddress));
            })
            .flatMap(shippingAddressRepository::delete);
    }

    @Transactional
    public Mono<Void> setDefaultShippingAddress(Long userId, Long newDefaultAddressId) {
        String query = "UPDATE shipping_addresses " +
            "SET is_default_address = CASE WHEN id = :newDefaultAddressId THEN TRUE ELSE FALSE END "
            +
            "WHERE user_id = :userId";

        return databaseClient.sql(query)
            .bind("newDefaultAddressId", newDefaultAddressId)
            .bind("userId", userId)
            .then();
    }
}
