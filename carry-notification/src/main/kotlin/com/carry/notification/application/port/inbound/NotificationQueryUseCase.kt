package com.carry.notification.application.port.inbound

import com.carry.notification.domain.model.Notification

interface NotificationQueryUseCase {
    fun getNotification(notificationId: Long): Notification
    fun getNotificationsByRecipient(recipientId: Long): List<Notification>
}
