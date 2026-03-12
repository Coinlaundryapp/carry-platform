package com.carry.notification.adapter.inbound.rest.dto

import com.carry.notification.domain.model.Notification
import java.time.Instant

data class NotificationResponse(
    val id: Long,
    val recipientId: Long,
    val recipientContact: String,
    val type: String,
    val channel: String,
    val title: String,
    val content: String,
    val status: String,
    val referenceType: String?,
    val referenceId: Long?,
    val sentAt: Instant?,
    val failReason: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(notification: Notification) = NotificationResponse(
            id = notification.id!!,
            recipientId = notification.recipientId,
            recipientContact = notification.recipientContact,
            type = notification.type.name,
            channel = notification.channel.name,
            title = notification.title,
            content = notification.content,
            status = notification.status.name,
            referenceType = notification.referenceType,
            referenceId = notification.referenceId,
            sentAt = notification.sentAt,
            failReason = notification.failReason,
            createdAt = notification.createdAt,
        )
    }
}
