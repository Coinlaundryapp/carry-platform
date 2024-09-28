package org.example.coin_laundry_app_backend.user.presentation.payload.request.availability;

import org.example.coin_laundry_app_backend.user.domain.enums.NotificationType;

public record CreateNotificationRequest(
        Region region,
        NotificationType notificationType,
        String contact
) {
    public record Region(String city, String district) {}
}