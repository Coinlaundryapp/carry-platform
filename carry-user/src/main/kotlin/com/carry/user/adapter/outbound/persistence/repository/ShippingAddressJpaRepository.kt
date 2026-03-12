package com.carry.user.adapter.outbound.persistence.repository

import com.carry.user.adapter.outbound.persistence.entity.ShippingAddressJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ShippingAddressJpaRepository : JpaRepository<ShippingAddressJpaEntity, Long> {

    fun findByUserId(userId: Long): List<ShippingAddressJpaEntity>

    fun findByUserIdAndIsDefaultTrue(userId: Long): Optional<ShippingAddressJpaEntity>

    fun countByUserId(userId: Long): Long
}
