package com.carry.notification.domain.model

import com.carry.notification.domain.vo.DevicePlatform
import java.time.Instant

class DeviceToken private constructor(
    val id: Long?,
    private var _userId: Long,
    val token: String,
    val platform: DevicePlatform,
    val createdAt: Instant,
    private var _lastSeenAt: Instant,
) {
    val userId get() = _userId
    val lastSeenAt get() = _lastSeenAt

    companion object {
        fun create(
            userId: Long,
            token: String,
            platform: DevicePlatform,
        ): DeviceToken {
            val now = Instant.now()
            return DeviceToken(
                id = null,
                _userId = userId,
                token = token,
                platform = platform,
                createdAt = now,
                _lastSeenAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            userId: Long,
            token: String,
            platform: DevicePlatform,
            createdAt: Instant,
            lastSeenAt: Instant,
        ): DeviceToken = DeviceToken(
            id = id,
            _userId = userId,
            token = token,
            platform = platform,
            createdAt = createdAt,
            _lastSeenAt = lastSeenAt,
        )
    }

    /** 동일 토큰 재등록 시: 소유자(다른 사용자로 이동 가능) 갱신 + 최근 접속 시각 갱신. */
    fun refresh(userId: Long) {
        _userId = userId
        _lastSeenAt = Instant.now()
    }
}
