package com.carry_laundry.carry_backend.order.repository;

import com.carry_laundry.carry_backend.order.domain.entity.Order;
import com.carry_laundry.carry_backend.order.repository.custom.OrderCustomRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends ReactiveCrudRepository<Order, Long>, OrderCustomRepository {
}
