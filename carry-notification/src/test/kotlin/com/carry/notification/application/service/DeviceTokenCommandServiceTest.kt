package com.carry.notification.application.service

import com.carry.notification.application.port.inbound.RegisterDeviceTokenCommand
import com.carry.notification.application.port.outbound.DeviceTokenPersistencePort
import com.carry.notification.domain.model.DeviceToken
import com.carry.notification.domain.vo.DevicePlatform
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DeviceTokenCommandServiceTest {

    private val deviceTokenPersistencePort = mockk<DeviceTokenPersistencePort>(relaxed = true)
    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sut = DeviceTokenCommandService(deviceTokenPersistencePort, clock)

    private fun command(userId: Long = 1L, token: String = "fcm-abc") =
        RegisterDeviceTokenCommand(userId = userId, token = token, platform = DevicePlatform.WEB)

    @Nested
    inner class Register {

        @Test
        fun `신규 토큰이면 새로 생성하여 저장한다`() {
            every { deviceTokenPersistencePort.findByToken("fcm-abc") } returns null
            val saved = slot<DeviceToken>()
            every { deviceTokenPersistencePort.save(capture(saved)) } answers { saved.captured }

            sut.register(command())

            assertThat(saved.captured.id).isNull()
            assertThat(saved.captured.userId).isEqualTo(1L)
            assertThat(saved.captured.token).isEqualTo("fcm-abc")
            verify(exactly = 1) { deviceTokenPersistencePort.save(any()) }
        }

        @Test
        fun `기존 토큰이면 소유자와 lastSeenAt을 갱신하여 저장한다(멱등 upsert)`() {
            val old = Instant.parse("2026-01-01T00:00:00Z")
            val existing = DeviceToken.reconstitute(
                id = 10L, userId = 1L, token = "fcm-abc", platform = DevicePlatform.WEB,
                createdAt = old, lastSeenAt = old,
            )
            every { deviceTokenPersistencePort.findByToken("fcm-abc") } returns existing
            val saved = slot<DeviceToken>()
            every { deviceTokenPersistencePort.save(capture(saved)) } answers { saved.captured }

            sut.register(command(userId = 2L))

            assertThat(saved.captured.id).isEqualTo(10L)
            assertThat(saved.captured.userId).isEqualTo(2L)
            assertThat(saved.captured.lastSeenAt).isEqualTo(now)
            verify(exactly = 1) { deviceTokenPersistencePort.save(any()) }
        }
    }
}
