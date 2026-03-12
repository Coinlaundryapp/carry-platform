package com.carry.notification.adapter.outbound.persistence.repository

import com.carry.notification.adapter.outbound.persistence.entity.NotificationJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface NotificationJpaRepository : JpaRepository<NotificationJpaEntity, Long> {

    @Query(
        "SELECT n FROM NotificationJpaEntity n WHERE n.recipientId = :recipientId" +
            " AND (:cursor IS NULL OR n.id < :cursor) ORDER BY n.id DESC",
    )
    fun findByRecipientIdWithCursor(
        recipientId: Long,
        cursor: Long?,
        pageable: Pageable,
    ): List<NotificationJpaEntity>
}
