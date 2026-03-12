package com.carry.notification.application.service

import com.carry.notification.application.port.inbound.NotificationQueryUseCase
import com.carry.notification.application.port.outbound.NotificationPersistencePort
import com.carry.notification.domain.exception.NotificationNotFoundException
import com.carry.notification.domain.model.Notification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NotificationQueryService(
    private val notificationPersistencePort: NotificationPersistencePort,
) : NotificationQueryUseCase {

    override fun getNotification(notificationId: Long): Notification {
        return notificationPersistencePort.findById(notificationId)
            ?: throw NotificationNotFoundException(notificationId)
    }

    override fun getNotificationsByRecipient(recipientId: Long): List<Notification> {
        return notificationPersistencePort.findByRecipientId(recipientId)
    }
}
