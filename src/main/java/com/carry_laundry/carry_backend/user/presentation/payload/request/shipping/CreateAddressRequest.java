package com.carry_laundry.carry_backend.user.presentation.payload.request.shipping;

import com.carry_laundry.carry_backend.user.domain.model.enums.EntranceType;

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