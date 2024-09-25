package org.example.coin_laundry_app_backend.user.repository;

import org.example.coin_laundry_app_backend.user.domain.entity.data.AvailabilityNotificationData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ServiceAvailabilityNotificationRepository extends ReactiveCrudRepository<AvailabilityNotificationData, Long> {
}
