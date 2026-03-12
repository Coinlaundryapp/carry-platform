package com.carry.dispatch.adapter.outbound.persistence.repository

import com.carry.dispatch.adapter.outbound.persistence.entity.PenaltyRecordJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PenaltyRecordJpaRepository : JpaRepository<PenaltyRecordJpaEntity, Long> {
    fun findByCarrierId(carrierId: Long): List<PenaltyRecordJpaEntity>
    fun countByCarrierId(carrierId: Long): Long
}
