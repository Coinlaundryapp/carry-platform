package com.carry.notification.application.service

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.notification.application.port.inbound.NotificationCommandUseCase
import com.carry.notification.application.port.inbound.NotificationEventHandler
import com.carry.notification.application.port.inbound.SendNotificationCommand
import com.carry.notification.domain.vo.NotificationChannel
import com.carry.notification.domain.vo.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationSagaHandler(
    private val notificationCommandUseCase: NotificationCommandUseCase,
) : NotificationEventHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun onOrderCreated(event: OrderCreatedEvent) {
        log.info("주문 생성 알림 처리: orderId={}, customerId={}", event.orderId, event.customerId)
        notificationCommandUseCase.send(
            SendNotificationCommand(
                recipientId = event.customerId,
                recipientContact = event.shippingAddress.recipientPhone,
                type = NotificationType.ORDER_CREATED,
                channel = NotificationChannel.KAKAO_ALARMTALK,
                title = "주문이 접수되었습니다",
                content = "주문번호 ${event.orderId}번이 정상적으로 접수되었습니다. 캐리어 배정을 진행합니다.",
                referenceType = "ORDER",
                referenceId = event.orderId,
            ),
        )
    }

    @Transactional
    override fun onDispatchAccepted(event: DispatchAcceptedEvent) {
        log.info("배차 수락 알림 처리: dispatchId={}, orderId={}", event.dispatchId, event.orderId)
        notificationCommandUseCase.send(
            SendNotificationCommand(
                recipientId = event.orderId,
                recipientContact = "",
                type = NotificationType.DISPATCH_ACCEPTED,
                channel = NotificationChannel.KAKAO_ALARMTALK,
                title = "캐리어가 배정되었습니다",
                content = "주문번호 ${event.orderId}번에 캐리어가 배정되었습니다. 곧 수거를 시작합니다.",
                referenceType = "DISPATCH",
                referenceId = event.dispatchId,
            ),
        )
    }

    @Transactional
    override fun onPickupCompleted(event: PickupCompletedEvent) {
        log.info("픽업 완료 알림 처리: deliveryId={}, orderId={}", event.deliveryId, event.orderId)
        notificationCommandUseCase.send(
            SendNotificationCommand(
                recipientId = event.customerId,
                recipientContact = "",
                type = NotificationType.PICKUP_COMPLETED,
                channel = NotificationChannel.KAKAO_ALARMTALK,
                title = "세탁물 수거가 완료되었습니다",
                content = "주문번호 ${event.orderId}번 세탁물 수거가 완료되었습니다. 세탁을 시작합니다.",
                referenceType = "DELIVERY",
                referenceId = event.deliveryId,
            ),
        )
    }

    @Transactional
    override fun onInvoiceIssued(event: InvoiceIssuedEvent) {
        log.info("청구서 발행 알림 처리: invoiceId={}, orderId={}", event.invoiceId, event.orderId)
        notificationCommandUseCase.send(
            SendNotificationCommand(
                recipientId = event.orderId,
                recipientContact = "",
                type = NotificationType.INVOICE_ISSUED,
                channel = NotificationChannel.KAKAO_ALARMTALK,
                title = "청구서가 발행되었습니다",
                content = "주문번호 ${event.orderId}번 청구서가 발행되었습니다. 결제 금액: ${event.totalAmount}원",
                referenceType = "ORDER",
                referenceId = event.orderId,
            ),
        )
    }

    @Transactional
    override fun onPaymentCompleted(event: PaymentCompletedEvent) {
        log.info("결제 완료 알림 처리: paymentId={}, orderId={}", event.paymentId, event.orderId)
        notificationCommandUseCase.send(
            SendNotificationCommand(
                recipientId = event.orderId,
                recipientContact = "",
                type = NotificationType.PAYMENT_COMPLETED,
                channel = NotificationChannel.KAKAO_ALARMTALK,
                title = "결제가 완료되었습니다",
                content = "주문번호 ${event.orderId}번 결제가 완료되었습니다. 결제 금액: ${event.amount}원",
                referenceType = "ORDER",
                referenceId = event.orderId,
            ),
        )
    }

    @Transactional
    override fun onDeliveryCompleted(event: DeliveryCompletedEvent) {
        log.info("배달 완료 알림 처리: deliveryId={}, orderId={}", event.deliveryId, event.orderId)
        notificationCommandUseCase.send(
            SendNotificationCommand(
                recipientId = event.orderId,
                recipientContact = "",
                type = NotificationType.DELIVERY_COMPLETED,
                channel = NotificationChannel.KAKAO_ALARMTALK,
                title = "배달이 완료되었습니다",
                content = "주문번호 ${event.orderId}번 세탁물 배달이 완료되었습니다. 이용해 주셔서 감사합니다.",
                referenceType = "DELIVERY",
                referenceId = event.deliveryId,
            ),
        )
    }
}
