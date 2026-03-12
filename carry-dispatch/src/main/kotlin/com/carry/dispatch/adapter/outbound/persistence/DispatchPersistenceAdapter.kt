package com.carry.dispatch.adapter.outbound.persistence

import com.carry.dispatch.adapter.outbound.persistence.entity.DispatchJpaEntity
import com.carry.dispatch.adapter.outbound.persistence.repository.DispatchJpaRepository
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.domain.model.Dispatch
import com.carry.dispatch.domain.vo.DispatchStatus
import org.springframework.stereotype.Component

@Component
class DispatchPersistenceAdapter(
    private val dispatchJpaRepository: DispatchJpaRepository,
) : DispatchPersistencePort {

    override fun save(dispatch: Dispatch): Dispatch {
        val entity = if (dispatch.id == null) {
            DispatchJpaEntity.fromDomain(dispatch)
        } else {
            val existing = dispatchJpaRepository.getReferenceById(dispatch.id)
            existing.updateFrom(dispatch)
            existing
        }
        return dispatchJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Dispatch? {
        return dispatchJpaRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByOrderId(orderId: Long): Dispatch? {
        return dispatchJpaRepository.findByOrderId(orderId)?.toDomain()
    }

    override fun findPendingByAreaCodes(areaCodes: List<String>): List<Dispatch> {
        return dispatchJpaRepository.findByStatusAndAreaCodeIn(DispatchStatus.PENDING, areaCodes)
            .map { it.toDomain() }
    }

    override fun findExpiredPendingDispatches(): List<Dispatch> {
        return dispatchJpaRepository.findExpiredPendingDispatches()
            .map { it.toDomain() }
    }

    override fun findByCarrierId(carrierId: Long): List<Dispatch> {
        return dispatchJpaRepository.findByCarrierIdOrderByCreatedAtDesc(carrierId)
            .map { it.toDomain() }
    }
}
