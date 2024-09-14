package org.example.coin_laundry_app_backend.user.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.user.application.record.shipping.ShippingSummary;
import org.example.coin_laundry_app_backend.user.domain.model.entity.ShippingAddress;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.shipping.CreateAddressRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.shipping.UpdateAddressRequest;
import org.example.coin_laundry_app_backend.user.repository.ShippingAddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@Transactional
@RequiredArgsConstructor
public class ShippingAddressService {

    private final ShippingAddressRepository shippingAddressRepository;

    public Mono<List<ShippingSummary>> getAllShippingAddresses(Long userId) {
        return shippingAddressRepository.findByUserId(userId)
            .map(ShippingSummary::of)
            .collectList();
    }

    public Mono<ShippingAddress> getShippingAddressById(Long userId, Long addressId) {
        return shippingAddressRepository.findByIdAndUserId(addressId, userId);
    }

    // TODO: How could we Calculate Coordinates using request.baseAddress()?
    public Mono<ShippingAddress> addShippingAddress(Long userId, CreateAddressRequest request) {
        return shippingAddressRepository.countByUserId(userId)
            .flatMap(count -> {
                ShippingAddress shippingAddress = request.toEntity(userId);
                if (count == 0) {
                    shippingAddress.markAsDefaultAddress();
                }
                return shippingAddressRepository.save(shippingAddress);
            });
    }

    public Mono<ShippingAddress> updateShippingAddress(Long userId, Long addressId,
        UpdateAddressRequest request) {
        return shippingAddressRepository.findByIdAndUserId(addressId, userId)
            .flatMap(existingAddress -> {
                existingAddress.overwrite(request.addressLabel(),
                    request.recipientName(),
                    request.recipientPhone(),
                    request.baseAddress(),
                    request.detailAddress(),
                    request.deliveryNotes(),
                    request.entranceType(),
                    request.entranceDetail());
                return shippingAddressRepository.save(existingAddress);
            });
    }

    public Mono<Void> deleteShippingAddress(Long userId, Long addressId) {
        return shippingAddressRepository.findByIdAndUserId(addressId, userId)
            .flatMap(shippingAddress -> {
                if (Boolean.TRUE.equals(shippingAddress.getIsDefaultAddress())) {
                    return Mono.error(
                        new RuntimeException("default shipping-address cannot be deleted"));
                }
                return Mono.just(shippingAddress);
            })
            .flatMap(shippingAddressRepository::delete);
    }


    public Mono<Void> setDefaultShippingAddress(Long userId, Long newDefaultAddressId) {
        return shippingAddressRepository.updateShippingAddressToDefault(userId,
            newDefaultAddressId);
    }
}
