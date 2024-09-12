package com.carry_laundry.carry_backend.user.domain.model.entity.domainmodel;

import com.carry_laundry.carry_backend.user.domain.model.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AvailabilityNotification {

    private Long id;
    private String city;
    private String district;
    private NotificationType notificationType;
    ;
    private String contact;

    public static AvailabilityNotification create(String city, String district,
        NotificationType notificationType, String contact) {
        return new AvailabilityNotification(null, city, district, notificationType, contact);
    }
}
