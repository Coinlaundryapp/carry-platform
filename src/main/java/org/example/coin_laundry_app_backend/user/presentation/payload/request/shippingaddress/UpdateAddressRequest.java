package org.example.coin_laundry_app_backend.user.presentation.payload.request.shippingaddress;

import org.example.coin_laundry_app_backend.user.domain.enums.EntranceType;

public record UpdateAddressRequest(
        String addressLabel,
        String recipientPhone,
        String recipientName,
        String baseAddress,
        String detailAddress,
        String deliveryNotes,
        EntranceType entranceType,
        String entranceDetail
) {}
