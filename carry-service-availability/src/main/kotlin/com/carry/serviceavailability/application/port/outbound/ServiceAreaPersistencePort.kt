package com.carry.serviceavailability.application.port.outbound

import com.carry.serviceavailability.domain.model.ServiceArea

interface ServiceAreaPersistencePort {
    fun save(serviceArea: ServiceArea): ServiceArea
    fun findById(id: Long): ServiceArea?
    fun findByAreaCode(areaCode: String): ServiceArea?
    fun findAllActive(): List<ServiceArea>
    fun existsByAreaCode(areaCode: String): Boolean
    fun delete(id: Long)
}
