package com.carry_laundry.carry_backend.user.repository;

import com.carry_laundry.carry_backend.user.domain.model.entity.data.AvailabilityNotificationData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ServiceAvailabilityNotificationRepository extends ReactiveCrudRepository<AvailabilityNotificationData, Long> {
}
