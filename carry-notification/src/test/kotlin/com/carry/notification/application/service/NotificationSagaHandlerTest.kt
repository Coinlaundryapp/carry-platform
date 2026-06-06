package com.carry.notification.application.service

import com.carry.event.payment.PaymentFailedEvent
import com.carry.notification.application.port.inbound.NotificationCommandUseCase
import com.carry.notification.application.port.inbound.SendNotificationCommand
import com.carry.notification.domain.vo.NotificationType
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationSagaHandlerTest {

    private val notificationCommandUseCase = mockk<NotificationCommandUseCase>(relaxed = true)
    private val sut = NotificationSagaHandler(notificationCommandUseCase)

    @Test
    fun `결제 실패 시 재결제 안내 알림을 발송한다`() {
        val command = slot<SendNotificationCommand>()
        sut.onPaymentFailed(PaymentFailedEvent(paymentId = 300L, orderId = 10L, reason = "잔액 부족"))

        verify { notificationCommandUseCase.send(capture(command)) }
        assertThat(command.captured.type).isEqualTo(NotificationType.PAYMENT_FAILED)
        assertThat(command.captured.referenceId).isEqualTo(10L)
    }
}
