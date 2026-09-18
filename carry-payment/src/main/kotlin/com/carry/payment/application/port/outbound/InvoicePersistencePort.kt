package com.carry.payment.application.port.outbound

import com.carry.payment.domain.model.Invoice
import java.time.Instant

interface InvoicePersistencePort {
    fun save(invoice: Invoice): Invoice
    fun findById(id: Long): Invoice?
    fun findByOrderId(orderId: Long): Invoice?

    /** cutoff 이전에 발행된 ISSUED 인보이스 목록(OverdueSweeper 연체 확정 대상). */
    fun findIssuedBefore(cutoff: Instant): List<Invoice>

    /** 고객에게 OVERDUE 인보이스가 있는지(신규 주문 생성 전제조건 체크용). */
    fun existsOverdueByCustomerId(customerId: Long): Boolean

    /**
     * ISSUED 상태인 경우에만 OVERDUE 로 조건부 갱신한다(동시에 PAID 로 바뀐 인보이스를
     * 덮어쓰지 않는 lost-update 방지). 갱신되면 true.
     */
    fun markOverdueIfIssued(invoiceId: Long, now: Instant): Boolean
}
