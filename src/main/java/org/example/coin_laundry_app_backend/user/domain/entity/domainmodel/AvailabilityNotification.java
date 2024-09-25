package org.example.coin_laundry_app_backend.user.domain.entity.domainmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.enums.NotificationType;

@Getter
@Builder
@AllArgsConstructor
public class AvailabilityNotification {

    private Long id;
    private String city;
    private String district;
    private NotificationType notificationType; ;
    private String contact;

    public static AvailabilityNotification create(String city, String district, NotificationType notificationType, String contact) {
        return new AvailabilityNotification(null, city, district, notificationType, contact);
    }
}
