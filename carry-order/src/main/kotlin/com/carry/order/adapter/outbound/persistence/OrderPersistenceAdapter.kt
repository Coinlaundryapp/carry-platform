package com.carry.order.adapter.outbound.persistence

import com.carry.order.adapter.outbound.persistence.entity.OrderJpaEntity
import com.carry.order.adapter.outbound.persistence.repository.OrderJpaRepository
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.model.Order
import org.springframework.stereotype.Component

@Component
class OrderPersistenceAdapter(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderPersistencePort {

    override fun save(order: Order): Order {
        val entity = if (order.id == null) {
            OrderJpaEntity.fromDomain(order)
        } else {
            val existing = orderJpaRepository.getReferenceById(order.id)
            existing.updateFrom(order)
            existing
        }
        return orderJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Order? {
        return orderJpaRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByCustomerId(customerId: Long): List<Order> {
        return orderJpaRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
            .map { it.toDomain() }
    }
}
