package com.carry.dispatch.adapter.outbound.persistence.repository

import com.carry.dispatch.adapter.outbound.persistence.entity.DispatchJpaEntity
import com.carry.dispatch.domain.vo.DispatchStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DispatchJpaRepository : JpaRepository<DispatchJpaEntity, Long> {

    fun findByOrderId(orderId: Long): DispatchJpaEntity?

    @Query(
        "SELECT d FROM DispatchJpaEntity d WHERE d.status = :status AND d.areaCode IN :areaCodes" +
            " AND (:cursor IS NULL OR d.id < :cursor) ORDER BY d.id DESC",
    )
    fun findPendingByAreaCodesWithCursor(
        @Param("status") status: DispatchStatus,
        @Param("areaCodes") areaCodes: List<String>,
        @Param("cursor") cursor: Long?,
        pageable: Pageable,
    ): List<DispatchJpaEntity>

    @Query(
        "SELECT d FROM DispatchJpaEntity d WHERE d.carrierId = :carrierId" +
            " AND (:cursor IS NULL OR d.id < :cursor) ORDER BY d.id DESC",
    )
    fun findByCarrierIdWithCursor(
        @Param("carrierId") carrierId: Long,
        @Param("cursor") cursor: Long?,
        pageable: Pageable,
    ): List<DispatchJpaEntity>

    @Query(
        value = "SELECT d.* FROM dispatch_dispatches d WHERE d.status = 'PENDING' " +
            "AND d.desired_pickup_at <= CURRENT_TIMESTAMP + INTERVAL '30 minutes'",
        nativeQuery = true,
    )
    fun findExpiredPendingDispatches(): List<DispatchJpaEntity>
}
