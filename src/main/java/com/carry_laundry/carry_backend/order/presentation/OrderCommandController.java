package com.carry_laundry.carry_backend.order.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.order.application.service.OrderRequestService;
import com.carry_laundry.carry_backend.order.presentation.payload.request.CreateOrderRequest;
import com.carry_laundry.carry_backend.order.presentation.payload.response.CreateOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
@RestController
public class OrderCommandController {

    private final OrderRequestService orderRequestService;

    @PostMapping
    public Mono<ApiCommonResponse<?>> createOrder(@AuthenticationPrincipal Long userId,
        @RequestBody CreateOrderRequest request) {
        return orderRequestService.createOrder(request, userId)
            .map(CreateOrderResponse::create).map(ApiCommonResponse::createSuccessResponse);
    }
}
