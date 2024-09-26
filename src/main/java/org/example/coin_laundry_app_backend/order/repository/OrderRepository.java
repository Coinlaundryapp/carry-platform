package org.example.coin_laundry_app_backend.order.repository;

import org.example.coin_laundry_app_backend.order.domain.entity.Order;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {
}
