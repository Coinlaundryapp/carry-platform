package com.carry.payment.adapter.outbound.persistence

import com.carry.payment.adapter.outbound.persistence.entity.PaymentJpaEntity
import com.carry.payment.adapter.outbound.persistence.repository.PaymentJpaRepository
import com.carry.payment.application.port.outbound.PaymentPersistencePort
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import org.springframework.stereotype.Component
import java.time.Instant

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
        return paymentJpaRepository.findFirstByOrderIdOrderByIdDesc(orderId)?.toDomain()
    }

    override fun findByStatus(status: PaymentStatus): List<Payment> {
        return paymentJpaRepository.findByStatus(status).map { it.toDomain() }
    }

    override fun findByPgTransactionId(pgTransactionId: String): Payment? {
        return paymentJpaRepository.findFirstByPgTransactionIdOrderByIdDesc(pgTransactionId)?.toDomain()
    }

    override fun findByProviderAndStatusInWindow(
        provider: PgProvider,
        statuses: Collection<PaymentStatus>,
        from: Instant,
        to: Instant,
    ): List<Payment> {
        return paymentJpaRepository
            .findByPgProviderAndStatusInAndUpdatedAtBetween(provider, statuses, from, to)
            .map { it.toDomain() }
    }
}
