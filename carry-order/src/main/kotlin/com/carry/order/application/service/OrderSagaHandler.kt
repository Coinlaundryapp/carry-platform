package com.carry.order.application.service

import com.carry.event.delivery.DeliveryCompletedEvent
import com.carry.event.delivery.LaundryStartedEvent
import com.carry.event.delivery.PickupCompletedEvent
import com.carry.event.dispatch.DispatchAcceptedEvent
import com.carry.event.dispatch.DispatchCancelledEvent
import com.carry.event.dispatch.DispatchTimeoutEvent
import com.carry.event.payment.InvoiceIssuedEvent
import com.carry.event.payment.PaymentCompletedEvent
import com.carry.event.payment.PaymentFailedEvent
import com.carry.event.payment.RefundCompletedEvent
import com.carry.order.application.port.inbound.OrderSagaEventHandler
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.exception.OrderNotFoundException
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.CancelledBy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderSagaHandler(
    private val orderPersistencePort: OrderPersistencePort,
) : OrderSagaEventHandler {

    @Transactional
    override fun onDispatchAccepted(event: DispatchAcceptedEvent) {
        val order = findOrder(event.orderId)
        order.markDispatched(event.carrierId)
        orderPersistencePort.save(order)
    }

    @Transactional
    override fun onDispatchTimeout(event: DispatchTimeoutEvent) {
        val order = findOrder(event.orderId)
        if (order.isCancellable()) {
            order.cancel("배차 시간 초과", CancelledBy.SYSTEM)
            orderPersistencePort.save(order)
        }
    }

    @Transactional
    override fun onDispatchCancelled(event: DispatchCancelledEvent) {
        val order = findOrder(event.orderId)
        if (order.isCancellable()) {
            order.cancel(event.reason, CancelledBy.COORDINATOR)
            orderPersistencePort.save(order)
        }
    }

    @Transactional
    override fun onPickupCompleted(event: PickupCompletedEvent) {
        val order = findOrder(event.orderId)
        order.markPickedUp(event.actualWeight)
        orderPersistencePort.save(order)
    }

    @Transactional
    override fun onInvoiceIssued(event: InvoiceIssuedEvent) {
        val order = findOrder(event.orderId)
        order.markInvoiced(event.invoiceId, event.totalAmount)
        orderPersistencePort.save(order)
    }

    @Transactional
    override fun onPaymentCompleted(event: PaymentCompletedEvent) {
        val order = findOrder(event.orderId)
        order.markPaid()
        orderPersistencePort.save(order)
    }

    @Transactional
    override fun onPaymentFailed(event: PaymentFailedEvent) {
        // 결제 실패 시 상태 유지 — 고객에게 재결제 요청 알림 (Phase 4 carry-notification)
    }

    @Transactional
    override fun onLaundryStarted(event: LaundryStartedEvent) {
        val order = findOrder(event.orderId)
        order.markInProgress()
        orderPersistencePort.save(order)
    }

    @Transactional
    override fun onDeliveryCompleted(event: DeliveryCompletedEvent) {
        val order = findOrder(event.orderId)
        order.markCompleted()
        orderPersistencePort.save(order)
    }

    @Transactional
    override fun onRefundCompleted(event: RefundCompletedEvent) {
        // TODO: 환불 완료 시 별도 상태(REFUNDED) 처리 필요 시 추가
    }

    private fun findOrder(orderId: Long): Order {
        return orderPersistencePort.findById(orderId) ?: throw OrderNotFoundException(orderId)
    }
}
