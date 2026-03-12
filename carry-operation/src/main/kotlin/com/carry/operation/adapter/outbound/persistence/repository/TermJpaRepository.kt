package com.carry.operation.adapter.outbound.persistence.repository

import com.carry.operation.adapter.outbound.persistence.entity.TermJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface TermJpaRepository : JpaRepository<TermJpaEntity, Long> {
    fun findByActive(active: Boolean): List<TermJpaEntity>
    fun findByRequiredAndActive(required: Boolean, active: Boolean): List<TermJpaEntity>
}
