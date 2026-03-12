package com.carry.notification.application.service

import com.carry.notification.application.port.inbound.NotificationCommandUseCase
import com.carry.notification.application.port.inbound.SendNotificationCommand
import com.carry.notification.application.port.outbound.NotificationPersistencePort
import com.carry.notification.application.port.outbound.NotificationSenderPort
import com.carry.notification.domain.model.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationCommandService(
    private val notificationPersistencePort: NotificationPersistencePort,
    private val notificationSenderPort: NotificationSenderPort,
) : NotificationCommandUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun send(command: SendNotificationCommand): Notification {
        val notification = Notification.create(
            recipientId = command.recipientId,
            recipientContact = command.recipientContact,
            type = command.type,
            channel = command.channel,
            title = command.title,
            content = command.content,
            referenceType = command.referenceType,
            referenceId = command.referenceId,
        )

        val saved = notificationPersistencePort.save(notification)

        try {
            notificationSenderPort.send(
                channel = command.channel,
                contact = command.recipientContact,
                title = command.title,
                content = command.content,
            )
            saved.markSent()
        } catch (e: Exception) {
            log.error("알림 발송 실패: {}", e.message, e)
            saved.markFailed(e.message ?: "알 수 없는 오류")
        }

        return notificationPersistencePort.save(saved)
    }
}
