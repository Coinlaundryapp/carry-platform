package com.carry.dispatch.application.port.outbound

import com.carry.dispatch.domain.model.Dispatch
import com.carry.dispatch.domain.vo.DispatchStatus
import java.time.Instant

interface DispatchPersistencePort {
    fun save(dispatch: Dispatch): Dispatch
    fun findById(id: Long): Dispatch?
    fun findByOrderId(orderId: Long): Dispatch?
    fun findPendingByAreaCodes(areaCodes: List<String>, cursor: Long?, size: Int): List<Dispatch>
    /**
     * 만료 후보 프리필터 — `desiredPickupAt <= threshold` 인 PENDING 배차.
     * 최종 판정은 [com.carry.dispatch.domain.model.Dispatch.isExpired] 가 한다.
     */
    fun findExpiredPendingDispatches(threshold: Instant): List<Dispatch>
    fun findByCarrierId(carrierId: Long, cursor: Long?, size: Int): List<Dispatch>
    fun findForCoordinator(status: DispatchStatus?, areaCode: String?, cursor: Long?, size: Int): List<Dispatch>
}
