package com.carry.delivery.adapter.outbound.persistence.repository

import com.carry.delivery.adapter.outbound.persistence.entity.DeliveryJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DeliveryJpaRepository : JpaRepository<DeliveryJpaEntity, Long> {
    fun findByOrderId(orderId: Long): DeliveryJpaEntity?

    @Query(
        "SELECT d FROM DeliveryJpaEntity d WHERE d.carrierId = :carrierId" +
            " AND (:cursor IS NULL OR d.id < :cursor) ORDER BY d.id DESC",
    )
    fun findByCarrierIdWithCursor(
        @Param("carrierId") carrierId: Long,
        @Param("cursor") cursor: Long?,
        pageable: Pageable,
    ): List<DeliveryJpaEntity>
}
