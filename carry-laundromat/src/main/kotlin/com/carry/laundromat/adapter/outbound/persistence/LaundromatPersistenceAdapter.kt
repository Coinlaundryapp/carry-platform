package com.carry.laundromat.adapter.outbound.persistence

import com.carry.laundromat.adapter.outbound.persistence.entity.LaundromatJpaEntity
import com.carry.laundromat.adapter.outbound.persistence.repository.LaundromatJpaRepository
import com.carry.laundromat.application.port.outbound.LaundromatPersistencePort
import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.model.NearbyLaundromat
import org.springframework.stereotype.Component

@Component
class LaundromatPersistenceAdapter(
    private val laundromatJpaRepository: LaundromatJpaRepository,
) : LaundromatPersistencePort {

    override fun save(laundromat: Laundromat): Laundromat {
        val entity = if (laundromat.id != null) {
            val existing = laundromatJpaRepository.getReferenceById(laundromat.id)
            existing.updateFrom(laundromat)
            existing
        } else {
            LaundromatJpaEntity.fromDomain(laundromat)
        }
        return laundromatJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Laundromat? =
        laundromatJpaRepository.findById(id)
            .map { it.toDomain() }
            .orElse(null)

    override fun findNearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): List<NearbyLaundromat> {
        val nearbyRows = laundromatJpaRepository.findNearbyIds(latitude, longitude, radiusMeters)
        if (nearbyRows.isEmpty()) return emptyList()

        val distanceById = nearbyRows.associate { it.getId() to it.getDistance() }
        val entities = laundromatJpaRepository.findAllById(distanceById.keys)

        return entities
            .map { entity ->
                NearbyLaundromat(
                    laundromat = entity.toDomain(),
                    distanceMeters = distanceById[entity.id] ?: 0.0,
                )
            }
            .sortedBy { it.distanceMeters }
    }

    override fun delete(id: Long) {
        laundromatJpaRepository.deleteById(id)
    }
}
