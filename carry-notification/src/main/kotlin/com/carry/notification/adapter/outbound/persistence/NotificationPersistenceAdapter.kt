package com.carry.notification.adapter.outbound.persistence

import com.carry.notification.adapter.outbound.persistence.entity.NotificationJpaEntity
import com.carry.notification.adapter.outbound.persistence.repository.NotificationJpaRepository
import com.carry.notification.application.port.outbound.NotificationPersistencePort
import com.carry.notification.domain.model.Notification
import org.springframework.stereotype.Component

@Component
class NotificationPersistenceAdapter(
    private val notificationJpaRepository: NotificationJpaRepository,
) : NotificationPersistencePort {

    override fun save(notification: Notification): Notification {
        val entity = if (notification.id == null) {
            NotificationJpaEntity.fromDomain(notification)
        } else {
            val existing = notificationJpaRepository.getReferenceById(notification.id)
            existing.updateFrom(notification)
            existing
        }
        return notificationJpaRepository.save(entity).toDomain()
    }

    override fun findById(notificationId: Long): Notification? {
        return notificationJpaRepository.findById(notificationId).orElse(null)?.toDomain()
    }

    override fun findByRecipientId(recipientId: Long): List<Notification> {
        return notificationJpaRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
            .map { it.toDomain() }
    }
}
