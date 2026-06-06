package com.carry.order.adapter.outbound.persistence.repository

import com.carry.order.adapter.outbound.persistence.entity.OrderJpaEntity
import com.carry.order.domain.vo.OrderStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: Long): List<OrderJpaEntity>

    fun findByStatusAndUpdatedAtBefore(status: OrderStatus, cutoff: Instant): List<OrderJpaEntity>

    @Query(
        "SELECT o FROM OrderJpaEntity o WHERE o.customerId = :customerId" +
            " AND (:cursor IS NULL OR o.id < :cursor) ORDER BY o.id DESC",
    )
    fun findByCustomerIdWithCursor(
        @Param("customerId") customerId: Long,
        @Param("cursor") cursor: Long?,
        pageable: Pageable,
    ): List<OrderJpaEntity>
}
