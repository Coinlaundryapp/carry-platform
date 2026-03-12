package com.carry.payment.adapter.outbound.persistence.repository

import com.carry.payment.adapter.outbound.persistence.entity.InvoiceJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InvoiceJpaRepository : JpaRepository<InvoiceJpaEntity, Long> {
    fun findByOrderId(orderId: Long): InvoiceJpaEntity?
}
