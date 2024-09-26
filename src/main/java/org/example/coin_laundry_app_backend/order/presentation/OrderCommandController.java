package org.example.coin_laundry_app_backend.order.presentation;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.order.application.service.OrderRequestService;
import org.example.coin_laundry_app_backend.order.presentation.payload.request.CreateOrderRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
@RestController
public class OrderCommandController {

    private final OrderRequestService orderRequestService;

    @PostMapping
    public Mono<ApiCommonResponse<?>> createOrder(@AuthenticationPrincipal Long userId,
                                                  @RequestBody CreateOrderRequest request) {
        return orderRequestService.createOrder(request, userId).then(Mono.just(ApiCommonResponse.createSuccessResponse()));
    }
}
