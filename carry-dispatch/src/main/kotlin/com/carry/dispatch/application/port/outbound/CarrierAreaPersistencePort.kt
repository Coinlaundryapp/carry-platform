package com.carry.dispatch.application.port.outbound

import com.carry.dispatch.domain.model.CarrierArea

interface CarrierAreaPersistencePort {
    fun save(carrierArea: CarrierArea): CarrierArea
    fun findByCarrierId(carrierId: Long): List<CarrierArea>
    fun findActiveByAreaCode(areaCode: String): List<CarrierArea>
    fun findByCarrierIdAndAreaCode(carrierId: Long, areaCode: String): CarrierArea?
    fun delete(carrierArea: CarrierArea)
}
