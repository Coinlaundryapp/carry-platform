package com.carry_laundry.carry_backend.notification.application.service;

import com.carry_laundry.carry_backend.notification.domain.model.value.NotificationMessage;
import reactor.core.publisher.Mono;

public interface NotificationService {

    Mono<Void> sendToIndividual(NotificationMessage notificationMessage);
}
