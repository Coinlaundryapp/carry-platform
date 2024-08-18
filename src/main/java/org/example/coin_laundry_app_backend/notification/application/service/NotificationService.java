package org.example.coin_laundry_app_backend.notification.application.service;

import org.example.coin_laundry_app_backend.notification.domain.model.value.NotificationMessage;
import reactor.core.publisher.Mono;

public interface NotificationService {

    Mono<Void> sendToIndividual(NotificationMessage notificationMessage);
}
