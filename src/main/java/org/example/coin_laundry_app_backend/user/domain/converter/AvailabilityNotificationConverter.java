package org.example.coin_laundry_app_backend.user.domain.converter;

import org.example.coin_laundry_app_backend.user.domain.model.entity.data.AvailabilityNotificationData;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.AvailabilityNotification;

public class AvailabilityNotificationConverter {

    public static AvailabilityNotification toDomain(AvailabilityNotificationData availabilityNotificationData) {
        return AvailabilityNotification.builder()
                .id(availabilityNotificationData.getId())
                .city(availabilityNotificationData.getCity())
                .district(availabilityNotificationData.getDistrict())
                .notificationType(availabilityNotificationData.getNotificationType())
                .contact(availabilityNotificationData.getContact())
                .build();
    }

    public static AvailabilityNotificationData toData(AvailabilityNotification availabilityNotification) {
        return AvailabilityNotificationData.builder()
                .id(availabilityNotification.getId())
                .city(availabilityNotification.getCity())
                .district(availabilityNotification.getDistrict())
                .notificationType(availabilityNotification.getNotificationType())
                .contact(availabilityNotification.getContact())
                .build();
    }
}
