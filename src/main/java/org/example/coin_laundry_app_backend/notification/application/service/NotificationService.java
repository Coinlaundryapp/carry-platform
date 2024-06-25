package org.example.coin_laundry_app_backend.notification.application.service;

import org.example.coin_laundry_app_backend.notification.domain.model.value.NotificationMessage;

public interface NotificationService {

    Void sendToIndividual(NotificationMessage notificationMessage);
}
