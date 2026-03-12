package com.carry.dispatch.adapter.outbound.persistence

import com.carry.dispatch.adapter.outbound.persistence.entity.CarrierAreaJpaEntity
import com.carry.dispatch.adapter.outbound.persistence.repository.CarrierAreaJpaRepository
import com.carry.dispatch.application.port.outbound.CarrierAreaPersistencePort
import com.carry.dispatch.domain.model.CarrierArea
import org.springframework.stereotype.Component

@Component
class CarrierAreaPersistenceAdapter(
    private val carrierAreaJpaRepository: CarrierAreaJpaRepository,
) : CarrierAreaPersistencePort {

    override fun save(carrierArea: CarrierArea): CarrierArea {
        val entity = if (carrierArea.id == null) {
            CarrierAreaJpaEntity.fromDomain(carrierArea)
        } else {
            val existing = carrierAreaJpaRepository.getReferenceById(carrierArea.id)
            existing.updateFrom(carrierArea)
            existing
        }
        return carrierAreaJpaRepository.save(entity).toDomain()
    }

    override fun findByCarrierId(carrierId: Long): List<CarrierArea> {
        return carrierAreaJpaRepository.findByCarrierId(carrierId)
            .map { it.toDomain() }
    }

    override fun findActiveByAreaCode(areaCode: String): List<CarrierArea> {
        return carrierAreaJpaRepository.findByAreaCodeAndActiveTrue(areaCode)
            .map { it.toDomain() }
    }

    override fun findByCarrierIdAndAreaCode(carrierId: Long, areaCode: String): CarrierArea? {
        return carrierAreaJpaRepository.findByCarrierIdAndAreaCode(carrierId, areaCode)?.toDomain()
    }

    override fun delete(carrierArea: CarrierArea) {
        carrierAreaJpaRepository.deleteById(carrierArea.id!!)
    }
}
