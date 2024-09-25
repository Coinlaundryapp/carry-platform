package org.example.coin_laundry_app_backend.user.application.service;

import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.geo.application.service.GeocodingService;
import org.example.coin_laundry_app_backend.geo.domain.model.EPSG4326Coordinate;
import org.example.coin_laundry_app_backend.user.application.record.shippingaddress.ShippingAddressSummary;
import org.example.coin_laundry_app_backend.user.domain.entity.ShippingAddress;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.shippingaddress.CreateAddressRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.shippingaddress.UpdateAddressRequest;
import org.example.coin_laundry_app_backend.user.repository.ShippingAddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@Transactional
@RequiredArgsConstructor
public class ShippingAddressService {

    private final ShippingAddressRepository shippingAddressRepository;
    private final GeocodingService geocodingService;

    public Mono<List<ShippingAddressSummary>> getAllShippingAddresses(Long userId) {
        return shippingAddressRepository.findByUserId(userId)
            .map(ShippingAddressSummary::of)
            .collectList();
    }

    public Mono<ShippingAddress> getShippingAddressById(Long userId, Long addressId) {
        return shippingAddressRepository.findByIdAndUserId(addressId, userId);
    }

    public Mono<ShippingAddress> getDefaultShippingAddress(Long userId) {
        return shippingAddressRepository.findByUserIdAndIsDefaultAddressTrue(userId);
    }

    public Mono<ShippingAddress> addShippingAddress(Long userId, CreateAddressRequest request) {
        return shippingAddressRepository.countByUserId(userId)
            .flatMap(count -> {
                ShippingAddress shippingAddress = request.toEntity(userId);
                if (count == 0) {
                    shippingAddress.markAsDefaultAddress();
                }
                return geocodingService.getGeocoding(shippingAddress.getBaseAddress())
                    .switchIfEmpty(Mono.error(new NoSuchElementException("Invalid Address")))
                    .map(geoModels -> geoModels.get(0))
                    .map(geoModel -> {
                        EPSG4326Coordinate coordinates = geoModel.coordinate();
                        shippingAddress.updateCoordinates(coordinates.latitude(),
                            coordinates.longitude());
                        return shippingAddress;
                    });
            })
            .flatMap(shippingAddressRepository::save);
    }

    public Mono<ShippingAddress> updateShippingAddress(Long userId, Long addressId,
        UpdateAddressRequest request) {
        return shippingAddressRepository.findByIdAndUserId(addressId, userId)
            .flatMap(existingAddress -> {
                String previousBaseAddress = existingAddress.getBaseAddress();
                existingAddress.overwrite(request.addressLabel(),
                    request.recipientName(),
                    request.recipientPhone(),
                    request.baseAddress(),
                    request.detailAddress(),
                    request.deliveryNotes(),
                    request.entranceType(),
                    request.entranceDetail());
                if (!previousBaseAddress.equals(request.baseAddress())) {
                    return geocodingService.getGeocoding(existingAddress.getBaseAddress())
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Invalid Address")))
                        .map(geoModels -> geoModels.get(0))
                        .map(geoModel -> {
                            EPSG4326Coordinate coordinates = geoModel.coordinate();
                            existingAddress.updateCoordinates(coordinates.latitude(),
                                coordinates.longitude());
                            return existingAddress;
                        })
                        .flatMap(shippingAddressRepository::save);
                }
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
