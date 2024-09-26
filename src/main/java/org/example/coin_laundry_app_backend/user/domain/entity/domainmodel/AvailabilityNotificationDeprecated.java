package org.example.coin_laundry_app_backend.user.domain.entity.domainmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.enums.NotificationType;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.availability.CreateNotificationRequest;

@Getter
@Builder
@AllArgsConstructor
public class AvailabilityNotificationDeprecated {

    private Long id;
    private String city;
    private String district;
    private NotificationType notificationType;
    private String contact;

    public static AvailabilityNotificationDeprecated create(String city, String district, NotificationType notificationType, String contact) {
        return new AvailabilityNotificationDeprecated(null, city, district, notificationType, contact);
    }

    public static AvailabilityNotificationDeprecated of(CreateNotificationRequest request) {
        return new AvailabilityNotificationDeprecated(null,
                request.region().city(),
                request.region().district(),
                request.notificationType(),
                request.contact());
    }
}
