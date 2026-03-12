package com.carry.payment.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.payment.domain.vo.ChargeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "payment_invoice_line_items")
class InvoiceLineItemJpaEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    val invoice: InvoiceJpaEntity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val chargeType: ChargeType,

    @Column(nullable = false)
    val description: String,

    @Column(nullable = false)
    val amount: Long,
) : BaseEntity()
