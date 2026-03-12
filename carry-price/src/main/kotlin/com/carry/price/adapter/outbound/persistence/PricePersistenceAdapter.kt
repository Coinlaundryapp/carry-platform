package com.carry.price.adapter.outbound.persistence

import com.carry.price.adapter.outbound.persistence.entity.PricePolicyJpaEntity
import com.carry.price.adapter.outbound.persistence.repository.PricePolicyJpaRepository
import com.carry.price.application.port.outbound.PricePersistencePort
import com.carry.price.domain.model.PricePolicy
import com.carry.price.domain.vo.PriceCondition
import org.springframework.stereotype.Component

@Component
class PricePersistenceAdapter(
    private val pricePolicyJpaRepository: PricePolicyJpaRepository,
) : PricePersistencePort {

    override fun save(policy: PricePolicy): PricePolicy {
        val entity = if (policy.id != null) {
            val existing = pricePolicyJpaRepository.getReferenceById(policy.id)
            existing.updateFrom(policy)
            existing
        } else {
            PricePolicyJpaEntity.fromDomain(policy)
        }
        return pricePolicyJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): PricePolicy? =
        pricePolicyJpaRepository.findById(id)
            .map { it.toDomain() }
            .orElse(null)

    override fun findByCondition(condition: PriceCondition): PricePolicy? =
        pricePolicyJpaRepository
            .findByOrderUnitTypeAndOrderRequestTypeAndLaundryItemType(
                condition.orderUnitType,
                condition.orderRequestType,
                condition.laundryItemType,
            )
            .map { it.toDomain() }
            .orElse(null)

    override fun existsByCondition(condition: PriceCondition): Boolean =
        pricePolicyJpaRepository
            .existsByOrderUnitTypeAndOrderRequestTypeAndLaundryItemType(
                condition.orderUnitType,
                condition.orderRequestType,
                condition.laundryItemType,
            )

    override fun delete(id: Long) {
        pricePolicyJpaRepository.deleteById(id)
    }
}
