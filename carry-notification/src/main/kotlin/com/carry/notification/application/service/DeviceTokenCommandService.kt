package com.carry.notification.application.service

import com.carry.notification.application.port.inbound.RegisterDeviceTokenCommand
import com.carry.notification.application.port.inbound.RegisterDeviceTokenUseCase
import com.carry.notification.application.port.outbound.DeviceTokenPersistencePort
import com.carry.notification.domain.model.DeviceToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class DeviceTokenCommandService(
    private val deviceTokenPersistencePort: DeviceTokenPersistencePort,
    private val clock: Clock,
) : RegisterDeviceTokenUseCase {

    @Transactional
    override fun register(command: RegisterDeviceTokenCommand): DeviceToken {
        val existing = deviceTokenPersistencePort.findByToken(command.token)
        val deviceToken = if (existing == null) {
            DeviceToken.create(
                userId = command.userId,
                token = command.token,
                platform = command.platform,
                now = clock.instant(),
            )
        } else {
            existing.refresh(command.userId, clock.instant())
            existing
        }
        return deviceTokenPersistencePort.save(deviceToken)
    }
}
