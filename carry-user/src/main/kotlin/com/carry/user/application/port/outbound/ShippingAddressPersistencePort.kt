package com.carry.user.application.port.outbound

import com.carry.user.domain.model.ShippingAddress

interface ShippingAddressPersistencePort {

    fun save(address: ShippingAddress): ShippingAddress

    fun findById(id: Long): ShippingAddress?

    fun findByUserId(userId: Long): List<ShippingAddress>

    fun findDefaultByUserId(userId: Long): ShippingAddress?

    fun countByUserId(userId: Long): Long

    fun delete(address: ShippingAddress)
}
