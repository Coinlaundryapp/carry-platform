package com.carry.delivery.adapter.outbound.persistence

import com.carry.delivery.adapter.outbound.persistence.entity.DeliveryJpaEntity
import com.carry.delivery.adapter.outbound.persistence.repository.DeliveryJpaRepository
import com.carry.delivery.application.port.outbound.DeliveryPersistencePort
import com.carry.delivery.domain.model.Delivery
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class DeliveryPersistenceAdapter(
    private val deliveryJpaRepository: DeliveryJpaRepository,
) : DeliveryPersistencePort {

    @Transactional
    override fun save(delivery: Delivery): Delivery {
        val entity = if (delivery.id == null) {
            DeliveryJpaEntity.fromDomain(delivery)
        } else {
            val existing = deliveryJpaRepository.getReferenceById(delivery.id)
            existing.updateFrom(delivery)
            existing
        }
        return deliveryJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Delivery? {
        return deliveryJpaRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByOrderId(orderId: Long): Delivery? {
        return deliveryJpaRepository.findByOrderId(orderId)?.toDomain()
    }

    override fun findByCarrierId(carrierId: Long): List<Delivery> {
        return deliveryJpaRepository.findByCarrierIdOrderByCreatedAtDesc(carrierId)
            .map { it.toDomain() }
    }
}
