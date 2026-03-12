package com.carry.serviceavailability.adapter.outbound.persistence.repository

import com.carry.serviceavailability.adapter.outbound.persistence.entity.ServiceAreaJpaEntity
import com.carry.serviceavailability.domain.vo.AreaStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ServiceAreaJpaRepository : JpaRepository<ServiceAreaJpaEntity, Long> {
    fun findByAreaCode(areaCode: String): Optional<ServiceAreaJpaEntity>
    fun existsByAreaCode(areaCode: String): Boolean
    fun findAllByStatus(status: AreaStatus): List<ServiceAreaJpaEntity>
}
