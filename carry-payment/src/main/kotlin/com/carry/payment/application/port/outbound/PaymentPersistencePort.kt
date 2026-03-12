package com.carry.payment.application.port.outbound

import com.carry.payment.domain.model.Payment

interface PaymentPersistencePort {
    fun save(payment: Payment): Payment
    fun findById(id: Long): Payment?
    fun findByOrderId(orderId: Long): Payment?
}
