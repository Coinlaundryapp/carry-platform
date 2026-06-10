package com.carry.notification.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.notification.domain.model.Notification
import com.carry.notification.domain.vo.NotificationChannel
import com.carry.notification.domain.vo.NotificationMessage
import com.carry.notification.domain.vo.NotificationReference
import com.carry.notification.domain.vo.NotificationStatus
import com.carry.notification.domain.vo.NotificationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "notification_notifications")
class NotificationJpaEntity(
    @Column(nullable = false)
    val recipientId: Long,

    @Column(nullable = false, length = 100)
    val recipientContact: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val channel: NotificationChannel,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(nullable = false, length = 2000)
    val content: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: NotificationStatus,

    @Column(length = 50)
    val referenceType: String?,

    val referenceId: Long?,

    var sentAt: Instant?,

    @Column(length = 500)
    var failReason: String?,
) : BaseEntity() {

    fun toDomain(): Notification = Notification.reconstitute(
        id = id,
        recipientId = recipientId,
        recipientContact = recipientContact,
        type = type,
        channel = channel,
        message = NotificationMessage(title, content),
        status = status,
        reference = referenceType?.let { NotificationReference(it, referenceId!!) },
        sentAt = sentAt,
        failReason = failReason,
        createdAt = createdAt,
    )

    fun updateFrom(notification: Notification) {
        status = notification.status
        sentAt = notification.sentAt
        failReason = notification.failReason
    }

    companion object {
        fun fromDomain(notification: Notification): NotificationJpaEntity = NotificationJpaEntity(
            recipientId = notification.recipientId,
            recipientContact = notification.recipientContact,
            type = notification.type,
            channel = notification.channel,
            title = notification.title,
            content = notification.content,
            status = notification.status,
            referenceType = notification.referenceType,
            referenceId = notification.referenceId,
            sentAt = notification.sentAt,
            failReason = notification.failReason,
        )
    }
}
