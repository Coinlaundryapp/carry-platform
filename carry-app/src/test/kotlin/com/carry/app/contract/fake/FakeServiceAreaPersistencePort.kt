package com.carry.app.contract.fake

import com.carry.serviceavailability.application.port.outbound.ServiceAreaPersistencePort
import com.carry.serviceavailability.domain.model.ServiceArea

class FakeServiceAreaPersistencePort : ServiceAreaPersistencePort {
    private val byAreaCode = mutableMapOf<String, ServiceArea>()

    fun put(area: ServiceArea) {
        byAreaCode[area.areaCode] = area
    }

    override fun save(serviceArea: ServiceArea): ServiceArea = serviceArea
    override fun findById(id: Long): ServiceArea? = byAreaCode.values.find { it.id == id }
    override fun findByAreaCode(areaCode: String): ServiceArea? = byAreaCode[areaCode]
    override fun findAllActive(): List<ServiceArea> = byAreaCode.values.toList()
    override fun existsByAreaCode(areaCode: String): Boolean = areaCode in byAreaCode
    override fun delete(id: Long) { byAreaCode.values.removeIf { it.id == id } }
}
