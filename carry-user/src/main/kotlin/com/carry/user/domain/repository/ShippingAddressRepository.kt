package com.carry.user.domain.repository

import com.carry.user.domain.model.ShippingAddress
import org.springframework.data.jpa.repository.JpaRepository

interface ShippingAddressRepository : JpaRepository<ShippingAddress, Long> {
    fun findByUserId(userId: Long): List<ShippingAddress>
    fun findByUserIdAndIsDefaultTrue(userId: Long): ShippingAddress?
    fun countByUserId(userId: Long): Long
}
