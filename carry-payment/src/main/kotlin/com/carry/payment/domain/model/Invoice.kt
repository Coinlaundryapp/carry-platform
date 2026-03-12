package com.carry.payment.domain.model

import com.carry.payment.domain.exception.InvoiceAlreadyPaidException
import com.carry.payment.domain.vo.InvoiceLineItem
import com.carry.payment.domain.vo.InvoiceStatus
import java.math.BigDecimal
import java.time.Instant

class Invoice private constructor(
    val id: Long?,
    val orderId: Long,
    val customerId: Long,
    private var _status: InvoiceStatus,
    val lineItems: List<InvoiceLineItem>,
    val weight: BigDecimal,
    val totalAmount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val status get() = _status

    companion object {
        fun create(
            orderId: Long,
            customerId: Long,
            lineItems: List<InvoiceLineItem>,
            weight: BigDecimal,
        ): Invoice {
            require(lineItems.isNotEmpty()) { "청구 항목이 비어 있을 수 없습니다" }

            val totalAmount = lineItems.sumOf { it.amount }
            val now = Instant.now()
            return Invoice(
                id = null,
                orderId = orderId,
                customerId = customerId,
                _status = InvoiceStatus.ISSUED,
                lineItems = lineItems,
                weight = weight,
                totalAmount = totalAmount,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            orderId: Long,
            customerId: Long,
            status: InvoiceStatus,
            lineItems: List<InvoiceLineItem>,
            weight: BigDecimal,
            totalAmount: Long,
            createdAt: Instant,
            updatedAt: Instant,
        ): Invoice = Invoice(
            id, orderId, customerId, status, lineItems, weight, totalAmount, createdAt, updatedAt,
        )
    }

    fun markPaid() {
        transitTo(InvoiceStatus.PAID)
    }

    fun cancel() {
        transitTo(InvoiceStatus.CANCELLED)
    }

    fun refund() {
        transitTo(InvoiceStatus.REFUNDED)
    }

    private fun transitTo(target: InvoiceStatus) {
        check(_status.canTransitionTo(target)) {
            "청구서 상태 전이가 유효하지 않습니다: $_status → $target"
        }
        _status = target
    }
}
