package com.carry.dispatch.application.port.inbound

import com.carry.dispatch.domain.model.Dispatch

interface DispatchQueryUseCase {
    fun getDispatch(dispatchId: Long): Dispatch
    fun getDispatchByOrder(orderId: Long): Dispatch
    fun getAvailableDispatches(carrierId: Long, cursor: Long?, size: Int): List<Dispatch>
    fun getDispatchesByCarrier(carrierId: Long, cursor: Long?, size: Int): List<Dispatch>
}
