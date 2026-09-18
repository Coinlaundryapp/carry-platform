package com.carry.dispatch.application.port.inbound

import com.carry.dispatch.domain.model.Dispatch
import com.carry.dispatch.domain.vo.DispatchStatus

interface DispatchQueryUseCase {
    fun getDispatch(dispatchId: Long): Dispatch
    fun getDispatchByOrder(orderId: Long): Dispatch
    fun getAvailableDispatches(carrierId: Long, cursor: Long?, size: Int): List<Dispatch>
    fun getDispatchesByCarrier(carrierId: Long, cursor: Long?, size: Int): List<Dispatch>

    /** 코디네이터 운영용 — 상태·권역으로 필터해 전체 배차를 조회한다. */
    fun getDispatchesForCoordinator(status: DispatchStatus?, areaCode: String?, cursor: Long?, size: Int): List<Dispatch>
}
