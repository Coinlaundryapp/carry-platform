package org.example.coin_laundry_app_backend.user.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.availability.CreateNotificationRequest;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Builder
@AllArgsConstructor
@Table("service_availability_notifications")
public class AvailabilityNotification {

    @Id
    private Long id;
    private String city;
    private String district;
    private String notificationType;
    private String contact;

    public static AvailabilityNotification create(CreateNotificationRequest request) {
        return AvailabilityNotification.builder()
                .city(request.region().city())
                .district(request.region().district())
                .notificationType(String.valueOf(request.notificationType()))
                .contact(request.contact())
                .build();
    }
}
