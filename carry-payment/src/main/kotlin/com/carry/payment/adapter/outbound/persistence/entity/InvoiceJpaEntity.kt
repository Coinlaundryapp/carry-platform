package com.carry.payment.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.payment.domain.model.Invoice
import com.carry.payment.domain.vo.ChargeType
import com.carry.payment.domain.vo.InvoiceLineItem
import com.carry.payment.domain.vo.InvoiceStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "payment_invoices")
class InvoiceJpaEntity(
    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val customerId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: InvoiceStatus,

    @Column(nullable = false, precision = 10, scale = 2)
    val weight: BigDecimal,

    @Column(nullable = false)
    val totalAmount: Long,

    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val lineItems: MutableList<InvoiceLineItemJpaEntity> = mutableListOf(),
) : BaseEntity() {

    fun toDomain(): Invoice = Invoice.reconstitute(
        id = id,
        orderId = orderId,
        customerId = customerId,
        status = status,
        lineItems = lineItems.map { InvoiceLineItem(it.chargeType, it.description, it.amount) },
        weight = weight,
        totalAmount = totalAmount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun updateFrom(invoice: Invoice) {
        status = invoice.status
    }

    companion object {
        fun fromDomain(invoice: Invoice): InvoiceJpaEntity {
            val entity = InvoiceJpaEntity(
                orderId = invoice.orderId,
                customerId = invoice.customerId,
                status = invoice.status,
                weight = invoice.weight,
                totalAmount = invoice.totalAmount,
            )
            invoice.lineItems.forEach { item ->
                entity.lineItems.add(
                    InvoiceLineItemJpaEntity(
                        invoice = entity,
                        chargeType = item.chargeType,
                        description = item.description,
                        amount = item.amount,
                    ),
                )
            }
            return entity
        }
    }
}
