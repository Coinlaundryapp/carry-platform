package com.carry_laundry.carry_backend.user.presentation.payload.request.availability;

import com.carry_laundry.carry_backend.user.domain.model.enums.NotificationType;

public record CreateNotificationRequest(
        Region region,
        NotificationType notificationType,
        String contact
) {
    public record Region(String city, String district) {}
}