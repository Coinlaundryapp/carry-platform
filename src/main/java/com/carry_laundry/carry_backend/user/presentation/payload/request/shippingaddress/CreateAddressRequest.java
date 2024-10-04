package com.carry_laundry.carry_backend.user.presentation.payload.request.shippingaddress;

import com.carry_laundry.carry_backend.user.domain.entity.ShippingAddress;
import com.carry_laundry.carry_backend.user.domain.enums.EntranceType;

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
            0.0,
            0.0,
            deliveryNotes,
            entranceType,
            entranceDetail
        );
    }

}