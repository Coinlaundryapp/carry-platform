package com.carry.payment.adapter.outbound.persistence.repository

import com.carry.payment.adapter.outbound.persistence.entity.InvoiceLineItemJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InvoiceLineItemJpaRepository : JpaRepository<InvoiceLineItemJpaEntity, Long>
