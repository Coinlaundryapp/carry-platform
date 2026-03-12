package com.carry.notification.domain.model

import com.carry.notification.domain.vo.NotificationChannel
import com.carry.notification.domain.vo.NotificationStatus
import com.carry.notification.domain.vo.NotificationType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class NotificationTest {

    private val now = Instant.now()

    private fun createNotification() = Notification.create(
        recipientId = 1L,
        recipientContact = "01012345678",
        type = NotificationType.ORDER_CREATED,
        channel = NotificationChannel.KAKAO_ALARMTALK,
        title = "주문 접수",
        content = "주문이 접수되었습니다.",
        referenceType = "ORDER",
        referenceId = 100L,
    )

    private fun reconstitutedNotification(
        status: NotificationStatus = NotificationStatus.PENDING,
    ) = Notification.reconstitute(
        id = 1L,
        recipientId = 1L,
        recipientContact = "01012345678",
        type = NotificationType.ORDER_CREATED,
        channel = NotificationChannel.KAKAO_ALARMTALK,
        title = "주문 접수",
        content = "주문이 접수되었습니다.",
        status = status,
        referenceType = "ORDER",
        referenceId = 100L,
        sentAt = if (status == NotificationStatus.SENT) now else null,
        failReason = if (status == NotificationStatus.FAILED) "전송 실패" else null,
        createdAt = now,
    )

    @Nested
    inner class Create {

        @Test
        fun `알림을 생성하면 PENDING 상태이다`() {
            val notification = createNotification()

            assertThat(notification.id).isNull()
            assertThat(notification.recipientId).isEqualTo(1L)
            assertThat(notification.recipientContact).isEqualTo("01012345678")
            assertThat(notification.type).isEqualTo(NotificationType.ORDER_CREATED)
            assertThat(notification.channel).isEqualTo(NotificationChannel.KAKAO_ALARMTALK)
            assertThat(notification.title).isEqualTo("주문 접수")
            assertThat(notification.content).isEqualTo("주문이 접수되었습니다.")
            assertThat(notification.status).isEqualTo(NotificationStatus.PENDING)
            assertThat(notification.referenceType).isEqualTo("ORDER")
            assertThat(notification.referenceId).isEqualTo(100L)
            assertThat(notification.sentAt).isNull()
            assertThat(notification.failReason).isNull()
            assertThat(notification.createdAt).isNotNull()
        }
    }

    @Nested
    inner class MarkSent {

        @Test
        fun `알림을 발송 완료로 표시하면 SENT 상태가 된다`() {
            val notification = createNotification()
            notification.markSent()

            assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
            assertThat(notification.sentAt).isNotNull()
        }
    }

    @Nested
    inner class MarkFailed {

        @Test
        fun `알림을 발송 실패로 표시하면 FAILED 상태가 된다`() {
            val notification = createNotification()
            notification.markFailed("연결 시간 초과")

            assertThat(notification.status).isEqualTo(NotificationStatus.FAILED)
            assertThat(notification.failReason).isEqualTo("연결 시간 초과")
        }
    }

    @Nested
    inner class Reconstitute {

        @Test
        fun `기존 알림을 복원할 수 있다`() {
            val notification = reconstitutedNotification(NotificationStatus.SENT)

            assertThat(notification.id).isEqualTo(1L)
            assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
            assertThat(notification.sentAt).isEqualTo(now)
        }

        @Test
        fun `실패 상태의 알림을 복원할 수 있다`() {
            val notification = reconstitutedNotification(NotificationStatus.FAILED)

            assertThat(notification.id).isEqualTo(1L)
            assertThat(notification.status).isEqualTo(NotificationStatus.FAILED)
            assertThat(notification.failReason).isEqualTo("전송 실패")
        }
    }
}
