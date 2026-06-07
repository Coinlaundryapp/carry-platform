package com.carry.payment.adapter.outbound.persistence.repository

import com.carry.payment.adapter.outbound.persistence.entity.PaymentJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, Long> {
    // 재결제 시 주문당 결제 행이 여러 개(FAILED + COMPLETED) 생길 수 있으므로 최신 행("현재 결제")을 반환한다.
    // 단수 findByOrderId 는 다중 행에서 IncorrectResultSizeDataAccessException 을 던진다.
    fun findFirstByOrderIdOrderByIdDesc(orderId: Long): PaymentJpaEntity?
}
