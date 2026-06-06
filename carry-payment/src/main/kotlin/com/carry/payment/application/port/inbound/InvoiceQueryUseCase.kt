package com.carry.payment.application.port.inbound

import com.carry.payment.domain.model.Invoice

interface InvoiceQueryUseCase {
    fun getInvoice(invoiceId: Long): Invoice
    fun getInvoiceByOrder(orderId: Long, requestingUserId: Long): Invoice
}
