package com.carry.payment.adapter.outbound.persistence

import com.carry.payment.adapter.outbound.persistence.entity.PaymentJpaEntity
import com.carry.payment.adapter.outbound.persistence.repository.PaymentJpaRepository
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.domain.model.Payment
import org.springframework.stereotype.Component

@Component
class PaymentPersistenceAdapter(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentPersistencePort {

    override fun save(payment: Payment): Payment {
        val entity = if (payment.id == null) {
            PaymentJpaEntity.fromDomain(payment)
        } else {
            val existing = paymentJpaRepository.getReferenceById(payment.id)
            existing.updateFrom(payment)
            existing
        }
        return paymentJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Payment? {
        return paymentJpaRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByOrderId(orderId: Long): Payment? {
        return paymentJpaRepository.findByOrderId(orderId)?.toDomain()
    }
}
