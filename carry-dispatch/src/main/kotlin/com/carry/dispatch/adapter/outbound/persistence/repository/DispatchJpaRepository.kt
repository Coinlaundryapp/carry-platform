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
        value = "SELECT d.* FROM dispatch_dispatches d WHERE d.status = 'PENDING' " +
            "AND d.desired_pickup_at <= CURRENT_TIMESTAMP + INTERVAL '30 minutes'",
        nativeQuery = true,
    )
    fun findExpiredPendingDispatches(): List<DispatchJpaEntity>
}
