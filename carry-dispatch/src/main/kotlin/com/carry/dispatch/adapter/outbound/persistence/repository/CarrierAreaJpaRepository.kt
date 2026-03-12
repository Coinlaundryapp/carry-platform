package com.carry.dispatch.adapter.outbound.persistence.repository

import com.carry.dispatch.adapter.outbound.persistence.entity.CarrierAreaJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface CarrierAreaJpaRepository : JpaRepository<CarrierAreaJpaEntity, Long> {
    fun findByCarrierId(carrierId: Long): List<CarrierAreaJpaEntity>
    fun findByAreaCodeAndActiveTrue(areaCode: String): List<CarrierAreaJpaEntity>
    fun findByCarrierIdAndAreaCode(carrierId: Long, areaCode: String): CarrierAreaJpaEntity?
}
