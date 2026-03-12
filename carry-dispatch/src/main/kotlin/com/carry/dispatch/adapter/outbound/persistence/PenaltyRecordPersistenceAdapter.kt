package com.carry.dispatch.adapter.outbound.persistence

import com.carry.dispatch.adapter.outbound.persistence.entity.PenaltyRecordJpaEntity
import com.carry.dispatch.adapter.outbound.persistence.repository.PenaltyRecordJpaRepository
import com.carry.dispatch.application.port.outbound.PenaltyRecordPersistencePort
import com.carry.dispatch.domain.model.PenaltyRecord
import org.springframework.stereotype.Component

@Component
class PenaltyRecordPersistenceAdapter(
    private val penaltyRecordJpaRepository: PenaltyRecordJpaRepository,
) : PenaltyRecordPersistencePort {

    override fun save(penaltyRecord: PenaltyRecord): PenaltyRecord {
        val entity = PenaltyRecordJpaEntity.fromDomain(penaltyRecord)
        return penaltyRecordJpaRepository.save(entity).toDomain()
    }

    override fun findByCarrierId(carrierId: Long): List<PenaltyRecord> {
        return penaltyRecordJpaRepository.findByCarrierId(carrierId)
            .map { it.toDomain() }
    }

    override fun countByCarrierId(carrierId: Long): Long {
        return penaltyRecordJpaRepository.countByCarrierId(carrierId)
    }
}
