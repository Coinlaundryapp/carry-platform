package com.carry.dispatch.application.service

import com.carry.dispatch.application.port.inbound.DispatchQueryUseCase
import com.carry.dispatch.application.port.outbound.CarrierAreaPersistencePort
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.domain.exception.DispatchNotFoundException
import com.carry.dispatch.domain.model.Dispatch
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
            ?: throw RuntimeException("해당 주문의 배차를 찾을 수 없습니다: $orderId")
    }

    override fun getAvailableDispatches(carrierId: Long): List<Dispatch> {
        val areas = carrierAreaPersistencePort.findByCarrierId(carrierId)
        val areaCodes = areas.filter { it.active }.map { it.areaCode }
        if (areaCodes.isEmpty()) return emptyList()
        return dispatchPersistencePort.findPendingByAreaCodes(areaCodes)
    }

    override fun getDispatchesByCarrier(carrierId: Long): List<Dispatch> {
        return dispatchPersistencePort.findByCarrierId(carrierId)
    }
}
