package com.carry.notification.adapter.outbound.persistence.repository

import com.carry.notification.adapter.outbound.persistence.entity.DeviceTokenJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DeviceTokenJpaRepository : JpaRepository<DeviceTokenJpaEntity, Long> {
    fun findByToken(token: String): DeviceTokenJpaEntity?
    fun findByUserId(userId: Long): List<DeviceTokenJpaEntity>
}
