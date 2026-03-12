package com.carry.dispatch.adapter.outbound.persistence.repository

import com.carry.dispatch.adapter.outbound.persistence.entity.DispatchJpaEntity
import com.carry.dispatch.domain.vo.DispatchStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DispatchJpaRepository : JpaRepository<DispatchJpaEntity, Long> {

    fun findByOrderId(orderId: Long): DispatchJpaEntity?

    fun findByStatusAndAreaCodeIn(status: DispatchStatus, areaCodes: List<String>): List<DispatchJpaEntity>

    fun findByCarrierIdOrderByCreatedAtDesc(carrierId: Long): List<DispatchJpaEntity>

    @Query(
        "SELECT d FROM DispatchJpaEntity d WHERE d.status = 'PENDING' " +
            "AND d.desiredPickupAt <= CURRENT_TIMESTAMP + 30 * 60",
    )
    fun findExpiredPendingDispatches(): List<DispatchJpaEntity>
}
