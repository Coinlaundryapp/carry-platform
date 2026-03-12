package com.carry.notification.application.port.inbound

import com.carry.notification.domain.model.Notification
import com.carry.notification.domain.vo.NotificationChannel
import com.carry.notification.domain.vo.NotificationType

data class SendNotificationCommand(
    val recipientId: Long,
    val recipientContact: String,
    val type: NotificationType,
    val channel: NotificationChannel,
    val title: String,
    val content: String,
    val referenceType: String? = null,
    val referenceId: Long? = null,
)

interface NotificationCommandUseCase {
    fun send(command: SendNotificationCommand): Notification
}
