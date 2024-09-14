package org.example.coin_laundry_app_backend.user.presentation.payload.request.shipping;

import org.example.coin_laundry_app_backend.user.domain.model.entity.ShippingAddress;
import org.example.coin_laundry_app_backend.user.domain.model.enums.EntranceType;

public record CreateAddressRequest(
    String addressLabel,
    String recipientPhone,
    String recipientName,
    String baseAddress,
    String detailAddress,
    String deliveryNotes,
    EntranceType entranceType,
    String entranceDetail
) {

    public ShippingAddress toEntity(Long userId) {
        return new ShippingAddress(
            null,
            userId,
            false,
            addressLabel,
            recipientName,
            recipientPhone,
            baseAddress,
            detailAddress,
            deliveryNotes,
            entranceType,
            entranceDetail
        );
    }

}