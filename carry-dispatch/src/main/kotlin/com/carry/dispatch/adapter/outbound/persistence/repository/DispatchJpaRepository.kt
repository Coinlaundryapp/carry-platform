package com.carry.dispatch.adapter.outbound.persistence.repository

import com.carry.dispatch.adapter.outbound.persistence.entity.DispatchJpaEntity
import com.carry.dispatch.domain.vo.DispatchStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

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
        "SELECT d FROM DispatchJpaEntity d WHERE (:status IS NULL OR d.status = :status)" +
            " AND (:areaCode IS NULL OR d.areaCode = :areaCode)" +
            " AND (:cursor IS NULL OR d.id < :cursor) ORDER BY d.id DESC",
    )
    fun findForCoordinatorWithCursor(
        @Param("status") status: DispatchStatus?,
        @Param("areaCode") areaCode: String?,
        @Param("cursor") cursor: Long?,
        pageable: Pageable,
    ): List<DispatchJpaEntity>

    /**
     * 만료 후보 프리필터. 임계 시각은 호출자가 주입한다 — 예전에는
     * `CURRENT_TIMESTAMP + INTERVAL '30 minutes'` 로 SQL 이 정책값과 시계를 함께 쥐고 있어
     * 도메인(`Dispatch.isExpired`)과 값이 이중화되고, 주입된 Clock 도 무시됐다.
     */
    @Query(
        "SELECT d FROM DispatchJpaEntity d WHERE d.status = com.carry.dispatch.domain.vo.DispatchStatus.PENDING " +
            "AND d.desiredPickupAt <= :threshold",
    )
    fun findExpiredPendingDispatches(@Param("threshold") threshold: Instant): List<DispatchJpaEntity>
}
