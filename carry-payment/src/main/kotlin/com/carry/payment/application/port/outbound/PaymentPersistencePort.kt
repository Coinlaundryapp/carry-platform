package com.carry.payment.application.port.outbound

import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PaymentStatus

interface PaymentPersistencePort {
    fun save(payment: Payment): Payment
    fun findById(id: Long): Payment?
    fun findByOrderId(orderId: Long): Payment?

    /** 특정 상태의 결제 목록(예: 환불 재시도 스위퍼가 REFUND_PENDING 조회). */
    fun findByStatus(status: PaymentStatus): List<Payment>
}
