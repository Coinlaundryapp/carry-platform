package com.carry.app.contract.fake

import com.carry.user.application.port.outbound.ShippingAddressPersistencePort
import com.carry.user.domain.model.ShippingAddress

class FakeShippingAddressPersistencePort : ShippingAddressPersistencePort {
    private val byId = mutableMapOf<Long, ShippingAddress>()

    fun put(id: Long, address: ShippingAddress) {
        byId[id] = address
    }

    override fun save(address: ShippingAddress): ShippingAddress = address
    override fun findById(id: Long): ShippingAddress? = byId[id]
    override fun findByUserId(userId: Long): List<ShippingAddress> = byId.values.filter { it.userId == userId }
    override fun findDefaultByUserId(userId: Long): ShippingAddress? = byId.values.find { it.userId == userId && it.isDefault }
    override fun countByUserId(userId: Long): Long = byId.values.count { it.userId == userId }.toLong()
    override fun delete(address: ShippingAddress) { byId.values.removeIf { it.id == address.id } }
}
