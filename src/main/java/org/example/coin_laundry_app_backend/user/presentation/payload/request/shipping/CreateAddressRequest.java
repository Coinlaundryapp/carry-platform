package org.example.coin_laundry_app_backend.user.presentation.payload.request.shipping;

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
) {}