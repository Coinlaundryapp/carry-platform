package com.carry.payment.application.port.outbound

import com.carry.payment.domain.model.Invoice

interface InvoicePersistencePort {
    fun save(invoice: Invoice): Invoice
    fun findById(id: Long): Invoice?
    fun findByOrderId(orderId: Long): Invoice?
}
