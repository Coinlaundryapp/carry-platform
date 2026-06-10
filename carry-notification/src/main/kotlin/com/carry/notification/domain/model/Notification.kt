package com.carry.notification.domain.model

import com.carry.notification.domain.vo.NotificationChannel
import com.carry.notification.domain.vo.NotificationMessage
import com.carry.notification.domain.vo.NotificationReference
import com.carry.notification.domain.vo.NotificationStatus
import com.carry.notification.domain.vo.NotificationType
import java.time.Instant

class Notification private constructor(
    val id: Long?,
    val recipientId: Long,
    val recipientContact: String,
    val type: NotificationType,
    val channel: NotificationChannel,
    val message: NotificationMessage,
    private var _status: NotificationStatus,
    val reference: NotificationReference?,
    private var _sentAt: Instant?,
    private var _failReason: String?,
    val createdAt: Instant,
) {
    val status get() = _status
    val sentAt get() = _sentAt
    val failReason get() = _failReason
    val title: String get() = message.title
    val content: String get() = message.content
    val referenceType: String? get() = reference?.type
    val referenceId: Long? get() = reference?.id

    companion object {
        fun create(
            recipientId: Long,
            recipientContact: String,
            type: NotificationType,
            channel: NotificationChannel,
            message: NotificationMessage,
            reference: NotificationReference? = null,
            now: Instant,
        ): Notification = Notification(
            id = null,
            recipientId = recipientId,
            recipientContact = recipientContact,
            type = type,
            channel = channel,
            message = message,
            _status = NotificationStatus.PENDING,
            reference = reference,
            _sentAt = null,
            _failReason = null,
            createdAt = now,
        )

        fun reconstitute(
            id: Long,
            recipientId: Long,
            recipientContact: String,
            type: NotificationType,
            channel: NotificationChannel,
            message: NotificationMessage,
            status: NotificationStatus,
            reference: NotificationReference?,
            sentAt: Instant?,
            failReason: String?,
            createdAt: Instant,
        ): Notification = Notification(
            id, recipientId, recipientContact, type, channel, message,
            status, reference, sentAt, failReason, createdAt,
        )
    }

    fun markSent(now: Instant) {
        _status = NotificationStatus.SENT
        _sentAt = now
    }

    fun markFailed(reason: String) {
        _status = NotificationStatus.FAILED
        _failReason = reason
    }
}
