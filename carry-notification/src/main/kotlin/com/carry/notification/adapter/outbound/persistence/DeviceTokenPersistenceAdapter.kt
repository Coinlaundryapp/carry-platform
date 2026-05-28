package com.carry.notification.adapter.outbound.persistence

import com.carry.notification.adapter.outbound.persistence.entity.DeviceTokenJpaEntity
import com.carry.notification.adapter.outbound.persistence.repository.DeviceTokenJpaRepository
import com.carry.notification.application.port.outbound.DeviceTokenPersistencePort
import com.carry.notification.domain.model.DeviceToken
import org.springframework.stereotype.Component

@Component
class DeviceTokenPersistenceAdapter(
    private val deviceTokenJpaRepository: DeviceTokenJpaRepository,
) : DeviceTokenPersistencePort {

    override fun save(deviceToken: DeviceToken): DeviceToken {
        val entity = if (deviceToken.id == null) {
            DeviceTokenJpaEntity.fromDomain(deviceToken)
        } else {
            val existing = deviceTokenJpaRepository.getReferenceById(deviceToken.id)
            existing.updateFrom(deviceToken)
            existing
        }
        return deviceTokenJpaRepository.save(entity).toDomain()
    }

    override fun findByToken(token: String): DeviceToken? =
        deviceTokenJpaRepository.findByToken(token)?.toDomain()

    override fun findActiveTokensByUserId(userId: Long): List<String> =
        deviceTokenJpaRepository.findByUserId(userId).map { it.token }
}
