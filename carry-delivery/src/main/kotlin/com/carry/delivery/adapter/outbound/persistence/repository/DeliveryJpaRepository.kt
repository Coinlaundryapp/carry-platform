package com.carry.delivery.adapter.outbound.persistence.repository

import com.carry.delivery.adapter.outbound.persistence.entity.DeliveryJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DeliveryJpaRepository : JpaRepository<DeliveryJpaEntity, Long> {
    fun findByOrderId(orderId: Long): DeliveryJpaEntity?
    fun findByCarrierIdOrderByCreatedAtDesc(carrierId: Long): List<DeliveryJpaEntity>
}
