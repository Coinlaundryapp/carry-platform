package com.carry_laundry.carry_backend.user.repository;

import com.carry_laundry.carry_backend.user.domain.entity.AvailabilityNotification;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceAvailabilityNotificationRepository extends ReactiveCrudRepository<AvailabilityNotification, Long> {
}
