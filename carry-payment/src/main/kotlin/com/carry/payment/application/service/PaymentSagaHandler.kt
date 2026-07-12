package com.carry.payment.application.service

import com.carry.common.logging.SagaLogContext
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.order.OrderCancelledEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.payment.application.port.inbound.PaymentCommandUseCase
import com.carry.payment.application.port.inbound.PaymentSagaEventHandler
import com.carry.payment.application.port.outbound.OrderStateQueryPort
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
    private val orderStateQueryPort: OrderStateQueryPort,
    private val autoChargeService: AutoChargeService,
) : PaymentSagaEventHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun onPickupCompleted(event: PickupCompletedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Payment saga: onPickupCompleted deliveryId={}", event.deliveryId)
            // 취소 선커밋 vs 픽업 race — 취소·종결된 주문에 유령 인보이스가 발행되지 않도록
            // 발행 전 주문 상태를 확인하고 조용히 skip(throw 하면 DLQ 로 빠진다).
            if (!orderStateQueryPort.isInvoiceable(event.orderId)) {
                log.warn("Payment saga: onPickupCompleted skip — 인보이스 발행 불가 주문 orderId={}", event.orderId)
                return@withOrderId
            }
            invoiceService.createInvoiceFromPickup(event)
        }
    }

    @Transactional
    override fun onOrderCancelled(event: OrderCancelledEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            val payment = paymentPersistencePort.findByOrderId(event.orderId)
            // 완료된 결제가 있을 때만 환불 대기로 표시. 선결제 없는 취소(CREATED/DISPATCHED 단계)나
            // 결제 실패 후 취소는 환불 대상이 없으므로 조용히 skip — throw 하면 DLQ 로 빠진다.
            // 실제 PG 환불은 RefundRetrySweeper 가 수행하므로, 여기서 PG 를 호출하지 않아
            // PG 장애와 무관하게 이 핸들러는 항상 성공한다(DLQ 위험 제거).
            if (payment != null && payment.status == PaymentStatus.COMPLETED) {
                log.info("Payment saga: onOrderCancelled — 환불 대기 표시 paymentId={} (재시도 스위퍼가 PG 환불 실행)", payment.id)
                paymentCommandUseCase.markRefundPending(event.orderId)
            } else {
                log.info("Payment saga: onOrderCancelled — 환불 대상 결제 없음, skip")
            }
        }
    }

    @Transactional
    override fun onInvoiceIssued(event: InvoiceIssuedEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Payment saga: onInvoiceIssued invoiceId={}", event.invoiceId)
            autoChargeService.chargeInvoice(event.invoiceId)
        }
    }
}
