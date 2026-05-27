package com.carry.notification.application.port.outbound

import com.carry.notification.domain.model.DeviceToken

interface DeviceTokenPersistencePort {
    fun save(deviceToken: DeviceToken): DeviceToken
    fun findByToken(token: String): DeviceToken?

    /** Phase 3(C4) PUSH 발송이 수신자 토큰을 조회할 때 사용. */
    fun findActiveTokensByUserId(userId: Long): List<String>
}
