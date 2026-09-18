package com.carry.notification.application.service

import com.carry.common.logging.SagaLogContext
import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.order.OrderCreatedEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.event.payment.PaymentFailedEvent
import com.carry.event.payment.RefundCompletedEvent
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
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Notification saga: onOrderCreated customerId={}", event.customerId)
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
    }

    @Transactional
    override fun onDispatchAccepted(event: DispatchAcceptedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Notification saga: onDispatchAccepted dispatchId={}", event.dispatchId)
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
    }

    @Transactional
    override fun onPickupCompleted(event: PickupCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Notification saga: onPickupCompleted deliveryId={}", event.deliveryId)
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
    }

    @Transactional
    override fun onInvoiceIssued(event: InvoiceIssuedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Notification saga: onInvoiceIssued invoiceId={} amount={}", event.invoiceId, event.totalAmount)
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
    }

    @Transactional
    override fun onPaymentCompleted(event: PaymentCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Notification saga: onPaymentCompleted paymentId={} amount={}", event.paymentId, event.amount)
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
    }

    @Transactional
    override fun onPaymentFailed(event: PaymentFailedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Notification saga: onPaymentFailed paymentId={} reason={}", event.paymentId, event.reason)
            notificationCommandUseCase.send(
                SendNotificationCommand(
                    recipientId = event.orderId,
                    recipientContact = "",
                    type = NotificationType.PAYMENT_FAILED,
                    channel = NotificationChannel.KAKAO_ALARMTALK,
                    title = "결제에 실패했습니다",
                    content = "결제 수단에 문제가 있어요. 카드를 다시 등록해 주세요. 세탁물은 정상적으로 배송됩니다.",
                    referenceType = "ORDER",
                    referenceId = event.orderId,
                ),
            )
        }
    }

    @Transactional
    override fun onRefundCompleted(event: RefundCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Notification saga: onRefundCompleted paymentId={} refundAmount={}", event.paymentId, event.refundAmount)
            notificationCommandUseCase.send(
                SendNotificationCommand(
                    recipientId = event.orderId,
                    recipientContact = "",
                    type = NotificationType.REFUND_COMPLETED,
                    channel = NotificationChannel.KAKAO_ALARMTALK,
                    title = "환불 완료",
                    content = "주문번호 ${event.orderId}번 환불이 완료되었습니다. 환불 금액: ${event.refundAmount}원",
                    referenceType = "ORDER",
                    referenceId = event.orderId,
                ),
            )
        }
    }

    @Transactional
    override fun onDeliveryCompleted(event: DeliveryCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Notification saga: onDeliveryCompleted deliveryId={}", event.deliveryId)
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
}
