package com.carry.notification.domain.model

import com.carry.notification.domain.vo.NotificationChannel
import com.carry.notification.domain.vo.NotificationStatus
import com.carry.notification.domain.vo.NotificationType
import java.time.Instant

class Notification private constructor(
    val id: Long?,
    val recipientId: Long,
    val recipientContact: String,
    val type: NotificationType,
    val channel: NotificationChannel,
    val title: String,
    val content: String,
    private var _status: NotificationStatus,
    val referenceType: String?,
    val referenceId: Long?,
    private var _sentAt: Instant?,
    private var _failReason: String?,
    val createdAt: Instant,
) {
    val status get() = _status
    val sentAt get() = _sentAt
    val failReason get() = _failReason

    companion object {
        fun create(
            recipientId: Long,
            recipientContact: String,
            type: NotificationType,
            channel: NotificationChannel,
            title: String,
            content: String,
            referenceType: String? = null,
            referenceId: Long? = null,
            now: Instant,
        ): Notification = Notification(
            id = null,
            recipientId = recipientId,
            recipientContact = recipientContact,
            type = type,
            channel = channel,
            title = title,
            content = content,
            _status = NotificationStatus.PENDING,
            referenceType = referenceType,
            referenceId = referenceId,
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
            title: String,
            content: String,
            status: NotificationStatus,
            referenceType: String?,
            referenceId: Long?,
            sentAt: Instant?,
            failReason: String?,
            createdAt: Instant,
        ): Notification = Notification(
            id, recipientId, recipientContact, type, channel, title, content,
            status, referenceType, referenceId, sentAt, failReason, createdAt,
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
