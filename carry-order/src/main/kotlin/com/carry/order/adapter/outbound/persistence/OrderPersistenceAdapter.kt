package com.carry.order.adapter.outbound.persistence

import com.carry.order.adapter.outbound.persistence.entity.OrderJpaEntity
import com.carry.order.adapter.outbound.persistence.repository.OrderJpaRepository
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.OrderStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant

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

    override fun findByCustomerId(customerId: Long, cursor: Long?, size: Int): List<Order> {
        return orderJpaRepository.findByCustomerIdWithCursor(customerId, cursor, PageRequest.of(0, size))
            .map { it.toDomain() }
    }

    override fun findByStatusAndUpdatedAtBefore(status: OrderStatus, cutoff: Instant): List<Order> {
        return orderJpaRepository.findByStatusAndUpdatedAtBefore(status, cutoff).map { it.toDomain() }
    }
}
