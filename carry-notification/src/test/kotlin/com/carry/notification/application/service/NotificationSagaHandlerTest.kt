package com.carry.notification.application.service

import com.carry.event.payment.PaymentFailedEvent
import com.carry.event.payment.RefundCompletedEvent
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
    fun `결제 실패 시 카드 재등록 안내 알림을 발송한다`() {
        val command = slot<SendNotificationCommand>()
        sut.onPaymentFailed(PaymentFailedEvent(paymentId = 300L, orderId = 10L, reason = "잔액 부족"))

        verify { notificationCommandUseCase.send(capture(command)) }
        assertThat(command.captured.type).isEqualTo(NotificationType.PAYMENT_FAILED)
        assertThat(command.captured.referenceId).isEqualTo(10L)
        assertThat(command.captured.content).isEqualTo("결제 수단에 문제가 있어요. 카드를 다시 등록해 주세요. 세탁물은 정상적으로 배송됩니다.")
    }

    @Test
    fun `환불 완료 시 환불 완료 알림을 발송한다`() {
        val command = slot<SendNotificationCommand>()
        sut.onRefundCompleted(RefundCompletedEvent(paymentId = 300L, orderId = 10L, refundAmount = 5000L))

        verify { notificationCommandUseCase.send(capture(command)) }
        assertThat(command.captured.type).isEqualTo(NotificationType.REFUND_COMPLETED)
        assertThat(command.captured.referenceId).isEqualTo(10L)
        assertThat(command.captured.content).contains("5000")
    }
}
