package org.example.coin_laundry_app_backend.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.user.application.record.shipping.ShippingSummary;
import org.example.coin_laundry_app_backend.user.domain.converter.ShippingAddressConverter;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.ShippingAddress;
import org.example.coin_laundry_app_backend.user.domain.model.enums.EntranceType;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.shipping.CreateAddressRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.shipping.UpdateAddressRequest;
import org.example.coin_laundry_app_backend.user.repository.ShippingAddressRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingAddressService {

    private final DatabaseClient databaseClient;
    private final ShippingAddressRepository shippingAddressRepository;

    public Mono<List<ShippingSummary>> getAllShippingAddresses(Long userId) {
        return shippingAddressRepository.findByUserId(userId).map(ShippingAddressConverter::toDomain)
                .map(ShippingSummary::of)
                .collectList();
    }

    public Mono<ShippingAddress> getShippingAddressById(Long userId, Long addressId) {
        return shippingAddressRepository.findByIdAndUserId(addressId, userId).map(ShippingAddressConverter::toDomain);
    }

    public Mono<ShippingAddress> addShippingAddress(Long userId, CreateAddressRequest request) {
        return shippingAddressRepository.countByUserId(userId)
                .flatMap(count -> {
                    ShippingAddress shippingAddress = ShippingAddress.create(
                            userId, request.addressLabel(), request.recipientName(), request.recipientPhone(),
                            request.baseAddress(), request.detailAddress(), request.deliveryNotes(), request.entranceType(), request.entranceDetail()
                    );
                    if (count == 0) {
                        shippingAddress.markAsDefaultAddress();
                    } else {
                        shippingAddress.clearDefaultAddress();
                    }
                    return shippingAddressRepository.save(ShippingAddressConverter.toData(shippingAddress));
                }).map(ShippingAddressConverter::toDomain);
    }

    public Mono<ShippingAddress> updateShippingAddress(Long userId, Long addressId, UpdateAddressRequest request) {
        return shippingAddressRepository.findByIdAndUserId(addressId, userId)
                .map(ShippingAddressConverter::toDomain)
                .flatMap(existingAddress -> {
                    existingAddress.overwrite(request.addressLabel(), request.recipientName(), request.recipientPhone(),
                            request.baseAddress(), request.detailAddress(), request.deliveryNotes(), request.entranceType(), request.entranceDetail());
                    return shippingAddressRepository.save(ShippingAddressConverter.toData(existingAddress));
                })
                .map(ShippingAddressConverter::toDomain);
    }

    public Mono<Void> deleteShippingAddress(Long userId, Long addressId) {
        return shippingAddressRepository.findById(addressId)
                .filter(address -> address.getUserId().equals(userId))
                .flatMap(shippingAddressRepository::delete);
    }

    public Mono<Void> setDefaultShippingAddress(Long userId, Long newDefaultAddressId) {
        String query = "UPDATE shipping_addresses " +
                "SET is_default_address = CASE WHEN id = :newDefaultAddressId THEN TRUE ELSE FALSE END " +
                "WHERE user_id = :userId";

        return databaseClient.sql(query)
                .bind("newDefaultAddressId", newDefaultAddressId)
                .bind("userId", userId)
                .then();
    }
}
