package com.carry.dispatch.application.port.outbound

import com.carry.dispatch.domain.model.PenaltyRecord

interface PenaltyRecordPersistencePort {
    fun save(penaltyRecord: PenaltyRecord): PenaltyRecord
    fun findByCarrierId(carrierId: Long): List<PenaltyRecord>
    fun countByCarrierId(carrierId: Long): Long
}
