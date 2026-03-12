package com.carry.notification.application.service

import com.carry.notification.application.port.inbound.SendNotificationCommand
import com.carry.notification.application.port.outbound.NotificationPersistencePort
import com.carry.notification.application.port.outbound.NotificationSenderPort
import com.carry.notification.domain.model.Notification
import com.carry.notification.domain.vo.NotificationChannel
import com.carry.notification.domain.vo.NotificationStatus
import com.carry.notification.domain.vo.NotificationType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class NotificationCommandServiceTest {

    private val notificationPersistencePort = mockk<NotificationPersistencePort>(relaxed = true)
    private val notificationSenderPort = mockk<NotificationSenderPort>(relaxed = true)

    private val sut = NotificationCommandService(notificationPersistencePort, notificationSenderPort)

    private val now = Instant.now()

    private fun createCommand() = SendNotificationCommand(
        recipientId = 1L,
        recipientContact = "01012345678",
        type = NotificationType.ORDER_CREATED,
        channel = NotificationChannel.KAKAO_ALARMTALK,
        title = "주문 접수",
        content = "주문이 접수되었습니다.",
        referenceType = "ORDER",
        referenceId = 100L,
    )

    @Nested
    inner class Send {

        @Test
        fun `알림을 발송하면 저장 후 발송하고 SENT 상태로 업데이트한다`() {
            val saved = slot<Notification>()
            every { notificationPersistencePort.save(capture(saved)) } answers {
                Notification.reconstitute(
                    id = 1L,
                    recipientId = saved.captured.recipientId,
                    recipientContact = saved.captured.recipientContact,
                    type = saved.captured.type,
                    channel = saved.captured.channel,
                    title = saved.captured.title,
                    content = saved.captured.content,
                    status = saved.captured.status,
                    referenceType = saved.captured.referenceType,
                    referenceId = saved.captured.referenceId,
                    sentAt = saved.captured.sentAt,
                    failReason = saved.captured.failReason,
                    createdAt = now,
                )
            }

            val result = sut.send(createCommand())

            assertThat(result.status).isEqualTo(NotificationStatus.SENT)
            assertThat(result.sentAt).isNotNull()
            verify { notificationSenderPort.send(NotificationChannel.KAKAO_ALARMTALK, "01012345678", "주문 접수", "주문이 접수되었습니다.") }
            verify(exactly = 2) { notificationPersistencePort.save(any()) }
        }

        @Test
        fun `발송 실패 시 FAILED 상태로 업데이트한다`() {
            val saved = slot<Notification>()
            every { notificationPersistencePort.save(capture(saved)) } answers {
                Notification.reconstitute(
                    id = 1L,
                    recipientId = saved.captured.recipientId,
                    recipientContact = saved.captured.recipientContact,
                    type = saved.captured.type,
                    channel = saved.captured.channel,
                    title = saved.captured.title,
                    content = saved.captured.content,
                    status = saved.captured.status,
                    referenceType = saved.captured.referenceType,
                    referenceId = saved.captured.referenceId,
                    sentAt = saved.captured.sentAt,
                    failReason = saved.captured.failReason,
                    createdAt = now,
                )
            }
            every {
                notificationSenderPort.send(any(), any(), any(), any())
            } throws RuntimeException("연결 시간 초과")

            val result = sut.send(createCommand())

            assertThat(result.status).isEqualTo(NotificationStatus.FAILED)
            assertThat(result.failReason).isEqualTo("연결 시간 초과")
            verify(exactly = 2) { notificationPersistencePort.save(any()) }
        }

        @Test
        fun `알림 저장 시 올바른 참조 정보가 포함된다`() {
            val saved = slot<Notification>()
            every { notificationPersistencePort.save(capture(saved)) } answers {
                Notification.reconstitute(
                    id = 1L,
                    recipientId = saved.captured.recipientId,
                    recipientContact = saved.captured.recipientContact,
                    type = saved.captured.type,
                    channel = saved.captured.channel,
                    title = saved.captured.title,
                    content = saved.captured.content,
                    status = saved.captured.status,
                    referenceType = saved.captured.referenceType,
                    referenceId = saved.captured.referenceId,
                    sentAt = saved.captured.sentAt,
                    failReason = saved.captured.failReason,
                    createdAt = now,
                )
            }

            val result = sut.send(createCommand())

            assertThat(result.referenceType).isEqualTo("ORDER")
            assertThat(result.referenceId).isEqualTo(100L)
            assertThat(result.type).isEqualTo(NotificationType.ORDER_CREATED)
            assertThat(result.channel).isEqualTo(NotificationChannel.KAKAO_ALARMTALK)
        }
    }
}
