package com.carry.payment.adapter.outbound.persistence

import com.carry.payment.adapter.outbound.persistence.entity.BillingKeyJpaEntity
import com.carry.payment.adapter.outbound.persistence.repository.BillingKeyJpaRepository
import com.carry.payment.application.port.outbound.BillingKeyPersistencePort
import com.carry.payment.domain.model.BillingKey
import com.carry.payment.domain.vo.BillingKeyStatus
import org.springframework.stereotype.Component

@Component
class BillingKeyPersistenceAdapter(
    private val billingKeyJpaRepository: BillingKeyJpaRepository,
) : BillingKeyPersistencePort {

    override fun save(billingKey: BillingKey): BillingKey {
        val entity = toEntity(billingKey)
        return billingKeyJpaRepository.save(entity).toDomain()
    }

    override fun saveAndFlush(billingKey: BillingKey): BillingKey {
        val entity = toEntity(billingKey)
        return billingKeyJpaRepository.saveAndFlush(entity).toDomain()
    }

    private fun toEntity(billingKey: BillingKey): BillingKeyJpaEntity {
        return if (billingKey.id == null) {
            BillingKeyJpaEntity.fromDomain(billingKey)
        } else {
            val existing = billingKeyJpaRepository.getReferenceById(billingKey.id)
            existing.updateFrom(billingKey)
            existing
        }
    }

    override fun findActiveByCustomerId(customerId: Long): BillingKey? {
        return billingKeyJpaRepository
            .findFirstByCustomerIdAndStatus(customerId, BillingKeyStatus.ACTIVE)
            ?.toDomain()
    }

    override fun existsActiveByCustomerId(customerId: Long): Boolean {
        return billingKeyJpaRepository.existsByCustomerIdAndStatus(customerId, BillingKeyStatus.ACTIVE)
    }
}
