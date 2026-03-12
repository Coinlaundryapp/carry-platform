package com.carry.dispatch.application.port.outbound

import com.carry.dispatch.domain.model.Dispatch

interface DispatchPersistencePort {
    fun save(dispatch: Dispatch): Dispatch
    fun findById(id: Long): Dispatch?
    fun findByOrderId(orderId: Long): Dispatch?
    fun findPendingByAreaCodes(areaCodes: List<String>, cursor: Long?, size: Int): List<Dispatch>
    fun findExpiredPendingDispatches(): List<Dispatch>
    fun findByCarrierId(carrierId: Long, cursor: Long?, size: Int): List<Dispatch>
}
