package com.carry_laundry.carry_backend.order.repository.custom;

import com.carry_laundry.carry_backend.order.domain.entity.Order;
import reactor.core.publisher.Mono;

public interface OrderCustomRepository {

    Mono<Order> getOrderDetail(Long orderId, Long userId);
}