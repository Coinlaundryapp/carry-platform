package com.carry.payment.adapter.outbound.persistence.repository

import com.carry.payment.adapter.outbound.persistence.entity.PaymentJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, Long> {
    fun findByOrderId(orderId: Long): PaymentJpaEntity?
}
