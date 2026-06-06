package com.carry.payment.application.service

import com.carry.common.logging.SagaLogContext
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.payment.application.port.inbound.PaymentCommandUseCase
import com.carry.payment.application.port.inbound.PaymentSagaEventHandler
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.domain.vo.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentSagaHandler(
    private val invoiceService: InvoiceService,
    private val paymentPersistencePort: PaymentPersistencePort,
    private val paymentCommandUseCase: PaymentCommandUseCase,
) : PaymentSagaEventHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun onPickupCompleted(event: PickupCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Payment saga: onPickupCompleted deliveryId={}", event.deliveryId)
            invoiceService.createInvoiceFromPickup(event)
        }
    }

    @Transactional
    override fun onOrderCancelled(event: OrderCancelledEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            val payment = paymentPersistencePort.findByOrderId(event.orderId)
            // 완료된 결제가 있을 때만 자동 환불. 선결제 없는 취소(CREATED/DISPATCHED 단계)나
            // 결제 실패 후 취소는 환불 대상이 없으므로 조용히 skip — throw 하면 DLQ 로 빠진다.
            if (payment != null && payment.status == PaymentStatus.COMPLETED) {
                log.info("Payment saga: onOrderCancelled — 자동 환불 요청 paymentId={}", payment.id)
                paymentCommandUseCase.requestRefund(event.orderId, event.reason)
            } else {
                log.info("Payment saga: onOrderCancelled — 환불 대상 결제 없음, skip")
            }
        }
    }
}
