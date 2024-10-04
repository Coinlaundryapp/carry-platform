package com.carry_laundry.carry_backend.order.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.order.application.service.OrderDetailService;
import com.carry_laundry.carry_backend.order.application.service.OrderInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
@RestController
public class OrderQueryController {

    private final OrderDetailService orderDetailService;
    private final OrderInvoiceService orderInvoiceService;

    @GetMapping("/{orderId}/details")
    public Mono<ApiCommonResponse<?>> queryOrderDetail(@AuthenticationPrincipal Long userId,
        @PathVariable Long orderId) {
        return orderDetailService.getDetail(orderId, userId)
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @GetMapping("/{orderId}/invoices")
    public Mono<ApiCommonResponse<?>> queryOrderInvoice(@AuthenticationPrincipal Long userId,
        @PathVariable Long orderId) {
        orderInvoiceService.getInvoice(orderId, userId);
        return Mono.just(ApiCommonResponse.createSuccessResponse());
    }
}
