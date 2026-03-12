package com.carry.order.adapter.outbound.persistence.repository

import com.carry.order.adapter.outbound.persistence.entity.OrderJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: Long): List<OrderJpaEntity>
}
