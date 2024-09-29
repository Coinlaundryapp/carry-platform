package com.carry_laundry.carry_backend.order.repository;

import com.carry_laundry.carry_backend.order.domain.entity.OrderSpecification;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderSpecificationRepository extends ReactiveCrudRepository<OrderSpecification, Long> {
}
