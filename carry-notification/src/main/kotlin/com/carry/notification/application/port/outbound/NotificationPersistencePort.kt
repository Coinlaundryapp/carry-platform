package com.carry.notification.application.port.outbound

import com.carry.notification.domain.model.Notification

interface NotificationPersistencePort {
    fun save(notification: Notification): Notification
    fun findById(notificationId: Long): Notification?
    fun findByRecipientId(recipientId: Long, cursor: Long?, size: Int): List<Notification>
}
