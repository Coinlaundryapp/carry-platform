package org.example.coin_laundry_app_backend.user.domain.model.entity.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.enums.NotificationType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Builder
@AllArgsConstructor
@Table("service_availability_notifications")
public class AvailabilityNotificationData {

    @Id
    private Long id;
    private String city;
    private String district;
    private NotificationType notificationType;
    private String contact;
}
