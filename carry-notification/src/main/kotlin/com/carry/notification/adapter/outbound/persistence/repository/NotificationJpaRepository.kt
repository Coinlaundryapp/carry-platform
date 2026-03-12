package com.carry.notification.adapter.outbound.persistence.repository

import com.carry.notification.adapter.outbound.persistence.entity.NotificationJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationJpaRepository : JpaRepository<NotificationJpaEntity, Long> {

    fun findByRecipientIdOrderByCreatedAtDesc(recipientId: Long): List<NotificationJpaEntity>
}
