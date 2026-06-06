package com.carry.order.domain.model

import com.carry.common.exception.requireInput
import com.carry.order.domain.exception.InvalidOrderStatusTransitionException
import com.carry.order.domain.exception.OrderNotCancellableException
import com.carry.order.domain.vo.CancelledBy
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.order.domain.vo.OrderStatus
import com.carry.order.domain.vo.SelectedOption
import java.math.BigDecimal
import java.time.Instant

class Order private constructor(
    val id: Long?,
    val customerId: Long,
    private var _status: OrderStatus,
    val laundromatId: Long,
    val laundryItemType: String,
    val selectedOptions: List<SelectedOption>,
    val shippingAddress: OrderShippingAddress,
    val desiredPickupAt: Instant,
    val desiredDeliveryAt: Instant,
    private var _carrierId: Long?,
    private var _invoiceId: Long?,
    private var _totalAmount: Long?,
    private var _actualWeight: BigDecimal?,
    private var _cancelReason: String?,
    private var _cancelledBy: CancelledBy?,
    private var _cancelledAt: Instant?,
    private var _completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val status get() = _status
    val carrierId get() = _carrierId
    val invoiceId get() = _invoiceId
    val totalAmount get() = _totalAmount
    val actualWeight get() = _actualWeight
    val cancelReason get() = _cancelReason
    val cancelledBy get() = _cancelledBy
    val cancelledAt get() = _cancelledAt
    val completedAt get() = _completedAt

    companion object {
        fun create(
            customerId: Long,
            laundromatId: Long,
            laundryItemType: String,
            selectedOptions: List<SelectedOption>,
            shippingAddress: OrderShippingAddress,
            desiredPickupAt: Instant,
            desiredDeliveryAt: Instant,
        ): Order {
            requireInput(selectedOptions.isNotEmpty()) { "최소 하나의 옵션을 선택해야 합니다" }
            requireInput(desiredDeliveryAt.isAfter(desiredPickupAt)) { "배달 희망 시각은 수거 희망 시각 이후여야 합니다" }

            val now = Instant.now()
            return Order(
                id = null,
                customerId = customerId,
                _status = OrderStatus.CREATED,
                laundromatId = laundromatId,
                laundryItemType = laundryItemType,
                selectedOptions = selectedOptions,
                shippingAddress = shippingAddress,
                desiredPickupAt = desiredPickupAt,
                desiredDeliveryAt = desiredDeliveryAt,
                _carrierId = null,
                _invoiceId = null,
                _totalAmount = null,
                _actualWeight = null,
                _cancelReason = null,
                _cancelledBy = null,
                _cancelledAt = null,
                _completedAt = null,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            customerId: Long,
            status: OrderStatus,
            laundromatId: Long,
            laundryItemType: String,
            selectedOptions: List<SelectedOption>,
            shippingAddress: OrderShippingAddress,
            desiredPickupAt: Instant,
            desiredDeliveryAt: Instant,
            carrierId: Long?,
            invoiceId: Long?,
            totalAmount: Long?,
            actualWeight: BigDecimal?,
            cancelReason: String?,
            cancelledBy: CancelledBy?,
            cancelledAt: Instant?,
            completedAt: Instant?,
            createdAt: Instant,
            updatedAt: Instant,
        ): Order = Order(
            id, customerId, status, laundromatId, laundryItemType,
            selectedOptions, shippingAddress, desiredPickupAt, desiredDeliveryAt,
            carrierId, invoiceId, totalAmount, actualWeight,
            cancelReason, cancelledBy, cancelledAt, completedAt, createdAt, updatedAt,
        )
    }

    fun markDispatched(carrierId: Long) {
        transitTo(OrderStatus.DISPATCHED)
        _carrierId = carrierId
    }

    fun markPickedUp(actualWeight: BigDecimal) {
        transitTo(OrderStatus.PICKED_UP)
        _actualWeight = actualWeight
    }

    fun markInvoiced(invoiceId: Long, totalAmount: Long) {
        transitTo(OrderStatus.INVOICED)
        _invoiceId = invoiceId
        _totalAmount = totalAmount
    }

    fun markPaid() {
        transitTo(OrderStatus.PAID)
    }

    fun markPaymentFailed() {
        transitTo(OrderStatus.PAYMENT_FAILED)
    }

    fun markRefundPending() {
        transitTo(OrderStatus.REFUND_PENDING)
    }

    fun markRefunded() {
        transitTo(OrderStatus.REFUNDED)
    }

    fun markInProgress() {
        transitTo(OrderStatus.IN_PROGRESS)
    }

    fun markCompleted() {
        transitTo(OrderStatus.COMPLETED)
        _completedAt = Instant.now()
    }

    fun cancel(reason: String, by: CancelledBy) {
        if (!_status.isCancellable()) {
            throw OrderNotCancellableException(id, _status)
        }
        _status = OrderStatus.CANCELLED
        _cancelReason = reason
        _cancelledBy = by
        _cancelledAt = Instant.now()
    }

    fun isCancellable(): Boolean = _status.isCancellable()

    private fun transitTo(target: OrderStatus) {
        if (!_status.canTransitionTo(target)) {
            throw InvalidOrderStatusTransitionException(_status, target)
        }
        _status = target
    }
}
