package com.carry.payment.domain.model

import com.carry.common.exception.checkState
import com.carry.common.exception.requireInput
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
            now: Instant,
        ): Invoice {
            requireInput(lineItems.isNotEmpty()) { "청구 항목이 비어 있을 수 없습니다" }

            val totalAmount = lineItems.sumOf { it.amount }
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
        // 이미 결제된 청구서만 전용 코드로 구분한다(409 동일). 클라이언트 재시도 가이드(docs/14)가
        // INVOICE_ALREADY_PAID 를 "재시도 말고 최신 상태를 조회하라" 로 안내하므로, 일반 CONFLICT 로
        // 뭉뚱그리면 그 안내가 닿지 않는다. 그 외 비정상 전이는 transitTo 의 일반 충돌로 남긴다.
        if (_status == InvoiceStatus.PAID) throw InvoiceAlreadyPaidException(id)
        transitTo(InvoiceStatus.PAID)
    }

    /** 결제 재시도가 소진되지 않은 채 연체 임계를 넘긴 청구서를 미수금으로 확정한다(신규 주문 차단용). */
    fun markOverdue() {
        transitTo(InvoiceStatus.OVERDUE)
    }

    fun cancel() {
        transitTo(InvoiceStatus.CANCELLED)
    }

    fun refund() {
        transitTo(InvoiceStatus.REFUNDED)
    }

    private fun transitTo(target: InvoiceStatus) {
        checkState(_status.canTransitionTo(target)) {
            "청구서 상태 전이가 유효하지 않습니다: $_status → $target"
        }
        _status = target
    }
}
