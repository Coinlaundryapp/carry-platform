package com.carry.dispatch.application.service

import com.carry.dispatch.application.port.inbound.DispatchQueryUseCase
import com.carry.dispatch.application.port.outbound.CarrierAreaPersistencePort
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.dispatch.domain.exception.DispatchNotFoundException
import com.carry.dispatch.domain.model.Dispatch
import com.carry.dispatch.domain.vo.DispatchStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class DispatchQueryService(
    private val dispatchPersistencePort: DispatchPersistencePort,
    private val carrierAreaPersistencePort: CarrierAreaPersistencePort,
) : DispatchQueryUseCase {

    override fun getDispatch(dispatchId: Long): Dispatch {
        return dispatchPersistencePort.findById(dispatchId) ?: throw DispatchNotFoundException(dispatchId)
    }

    override fun getDispatchByOrder(orderId: Long): Dispatch {
        return dispatchPersistencePort.findByOrderId(orderId)
            ?: throw BusinessException(ErrorCode.DISPATCH_NOT_FOUND, "해당 주문의 배차를 찾을 수 없습니다: orderId=$orderId")
    }

    override fun getAvailableDispatches(carrierId: Long, cursor: Long?, size: Int): List<Dispatch> {
        val areas = carrierAreaPersistencePort.findByCarrierId(carrierId)
        val areaCodes = areas.filter { it.active }.map { it.areaCode }
        if (areaCodes.isEmpty()) return emptyList()
        return dispatchPersistencePort.findPendingByAreaCodes(areaCodes, cursor, size)
    }

    override fun getDispatchesByCarrier(carrierId: Long, cursor: Long?, size: Int): List<Dispatch> {
        return dispatchPersistencePort.findByCarrierId(carrierId, cursor, size)
    }

    override fun getDispatchesForCoordinator(
        status: DispatchStatus?,
        areaCode: String?,
        cursor: Long?,
        size: Int,
    ): List<Dispatch> {
        return dispatchPersistencePort.findForCoordinator(status, areaCode, cursor, size)
    }
}
