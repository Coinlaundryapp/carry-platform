package org.example.coin_laundry_app_backend.order.repository.custom;

import org.example.coin_laundry_app_backend.order.domain.entity.Order;
import reactor.core.publisher.Mono;

public interface OrderCustomRepository {
    Mono<Order> getOrderDetail(Long orderId, Long userId);
}