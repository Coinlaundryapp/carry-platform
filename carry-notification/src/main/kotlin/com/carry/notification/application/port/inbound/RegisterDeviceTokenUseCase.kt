package com.carry.notification.application.port.inbound

import com.carry.notification.domain.model.DeviceToken
import com.carry.notification.domain.vo.DevicePlatform

data class RegisterDeviceTokenCommand(
    val userId: Long,
    val token: String,
    val platform: DevicePlatform,
)

interface RegisterDeviceTokenUseCase {
    fun register(command: RegisterDeviceTokenCommand): DeviceToken
}
