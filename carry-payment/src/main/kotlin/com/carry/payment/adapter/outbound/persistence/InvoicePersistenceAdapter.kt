package com.carry.payment.adapter.outbound.persistence

import com.carry.payment.adapter.outbound.persistence.entity.InvoiceJpaEntity
import com.carry.payment.adapter.outbound.persistence.repository.InvoiceJpaRepository
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.domain.model.Invoice
import org.springframework.stereotype.Component

@Component
class InvoicePersistenceAdapter(
    private val invoiceJpaRepository: InvoiceJpaRepository,
) : InvoicePersistencePort {

    override fun save(invoice: Invoice): Invoice {
        val entity = if (invoice.id == null) {
            InvoiceJpaEntity.fromDomain(invoice)
        } else {
            val existing = invoiceJpaRepository.getReferenceById(invoice.id)
            existing.updateFrom(invoice)
            existing
        }
        return invoiceJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Invoice? {
        return invoiceJpaRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByOrderId(orderId: Long): Invoice? {
        return invoiceJpaRepository.findByOrderId(orderId)?.toDomain()
    }
}
