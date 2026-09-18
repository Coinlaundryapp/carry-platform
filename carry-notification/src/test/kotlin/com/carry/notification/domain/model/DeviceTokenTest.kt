package com.carry.notification.domain.model

import com.carry.notification.domain.vo.DevicePlatform
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DeviceTokenTest {

    private val now = Instant.parse("2026-06-07T00:00:00Z")

    @Test
    fun `create는 createdAt과 lastSeenAt을 설정하고 id는 null이다`() {
        val token = DeviceToken.create(userId = 1L, token = "fcm-abc", platform = DevicePlatform.WEB, now = now)

        assertThat(token.id).isNull()
        assertThat(token.userId).isEqualTo(1L)
        assertThat(token.token).isEqualTo("fcm-abc")
        assertThat(token.platform).isEqualTo(DevicePlatform.WEB)
        assertThat(token.createdAt).isEqualTo(now)
        assertThat(token.lastSeenAt).isEqualTo(now)
    }

    @Test
    fun `refresh는 소유자와 lastSeenAt을 갱신한다`() {
        val old = Instant.parse("2026-01-01T00:00:00Z")
        val token = DeviceToken.reconstitute(
            id = 1L, userId = 1L, token = "fcm-abc", platform = DevicePlatform.WEB,
            createdAt = old, lastSeenAt = old,
        )

        token.refresh(userId = 2L, now = now)

        assertThat(token.userId).isEqualTo(2L)
        assertThat(token.lastSeenAt).isEqualTo(now)
    }
}
