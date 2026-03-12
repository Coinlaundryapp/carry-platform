package com.carry.user.adapter.outbound.persistence

import com.carry.user.adapter.outbound.persistence.entity.ShippingAddressJpaEntity
import com.carry.user.adapter.outbound.persistence.repository.ShippingAddressJpaRepository
import com.carry.user.application.port.outbound.ShippingAddressPersistencePort
import com.carry.user.domain.model.ShippingAddress
import org.springframework.stereotype.Component

@Component
class ShippingAddressPersistenceAdapter(
    private val shippingAddressJpaRepository: ShippingAddressJpaRepository,
) : ShippingAddressPersistencePort {

    override fun save(address: ShippingAddress): ShippingAddress {
        val entity = if (address.id != null) {
            val existing = shippingAddressJpaRepository.getReferenceById(address.id)
            existing.updateFrom(address)
            existing
        } else {
            ShippingAddressJpaEntity.fromDomain(address)
        }
        return shippingAddressJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): ShippingAddress? =
        shippingAddressJpaRepository.findById(id)
            .map { it.toDomain() }
            .orElse(null)

    override fun findByUserId(userId: Long): List<ShippingAddress> =
        shippingAddressJpaRepository.findByUserId(userId)
            .map { it.toDomain() }

    override fun findDefaultByUserId(userId: Long): ShippingAddress? =
        shippingAddressJpaRepository.findByUserIdAndIsDefaultTrue(userId)
            .map { it.toDomain() }
            .orElse(null)

    override fun countByUserId(userId: Long): Long =
        shippingAddressJpaRepository.countByUserId(userId)

    override fun delete(address: ShippingAddress) {
        address.id?.let { shippingAddressJpaRepository.deleteById(it) }
    }
}
