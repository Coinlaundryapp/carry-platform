package com.carry.notification.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.notification.domain.model.DeviceToken
import com.carry.notification.domain.vo.DevicePlatform
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "notification_device_tokens")
class DeviceTokenJpaEntity(
    @Column(nullable = false)
    var userId: Long,

    @Column(nullable = false, length = 512, unique = true)
    val token: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val platform: DevicePlatform,

    @Column(nullable = false)
    var lastSeenAt: Instant,
) : BaseEntity() {

    fun toDomain(): DeviceToken = DeviceToken.reconstitute(
        id = id,
        userId = userId,
        token = token,
        platform = platform,
        createdAt = createdAt,
        lastSeenAt = lastSeenAt,
    )

    fun updateFrom(deviceToken: DeviceToken) {
        userId = deviceToken.userId
        lastSeenAt = deviceToken.lastSeenAt
    }

    companion object {
        fun fromDomain(deviceToken: DeviceToken): DeviceTokenJpaEntity = DeviceTokenJpaEntity(
            userId = deviceToken.userId,
            token = deviceToken.token,
            platform = deviceToken.platform,
            lastSeenAt = deviceToken.lastSeenAt,
        )
    }
}
