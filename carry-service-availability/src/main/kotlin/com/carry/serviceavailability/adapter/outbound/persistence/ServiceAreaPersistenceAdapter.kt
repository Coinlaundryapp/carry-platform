package com.carry.serviceavailability.adapter.outbound.persistence

import com.carry.serviceavailability.adapter.outbound.persistence.entity.ServiceAreaJpaEntity
import com.carry.serviceavailability.adapter.outbound.persistence.repository.ServiceAreaJpaRepository
import com.carry.serviceavailability.application.port.outbound.ServiceAreaPersistencePort
import com.carry.serviceavailability.domain.model.ServiceArea
import com.carry.serviceavailability.domain.vo.AreaStatus
import org.springframework.stereotype.Component

@Component
class ServiceAreaPersistenceAdapter(
    private val serviceAreaJpaRepository: ServiceAreaJpaRepository,
) : ServiceAreaPersistencePort {

    override fun save(serviceArea: ServiceArea): ServiceArea {
        val entity = if (serviceArea.id != null) {
            val existing = serviceAreaJpaRepository.getReferenceById(serviceArea.id)
            existing.updateFrom(serviceArea)
            existing
        } else {
            ServiceAreaJpaEntity.fromDomain(serviceArea)
        }
        return serviceAreaJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): ServiceArea? =
        serviceAreaJpaRepository.findById(id)
            .map { it.toDomain() }
            .orElse(null)

    override fun findByAreaCode(areaCode: String): ServiceArea? =
        serviceAreaJpaRepository.findByAreaCode(areaCode)
            .map { it.toDomain() }
            .orElse(null)

    override fun findAllActive(): List<ServiceArea> =
        serviceAreaJpaRepository.findAllByStatus(AreaStatus.ACTIVE)
            .map { it.toDomain() }

    override fun existsByAreaCode(areaCode: String): Boolean =
        serviceAreaJpaRepository.existsByAreaCode(areaCode)

    override fun delete(id: Long) {
        serviceAreaJpaRepository.deleteById(id)
    }
}
