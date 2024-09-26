package org.example.coin_laundry_app_backend.order.repository;

import org.example.coin_laundry_app_backend.order.domain.entity.OrderShippingAddress;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderShippingAddressRepository extends ReactiveCrudRepository<OrderShippingAddress, Long> {
}
