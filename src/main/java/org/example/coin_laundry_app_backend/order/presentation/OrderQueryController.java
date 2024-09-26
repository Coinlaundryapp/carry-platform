package org.example.coin_laundry_app_backend.order.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.order.application.service.OrderDetailService;
import org.example.coin_laundry_app_backend.order.application.service.OrderInvoiceService;
import org.example.coin_laundry_app_backend.order.domain.model.OptionCondition;
import org.example.coin_laundry_app_backend.order.presentation.payload.request.QueryPricePolicyRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
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
        orderDetailService.getDetail(orderId, userId);
        return Mono.just(ApiCommonResponse.createSuccessResponse());
    }

    @GetMapping("/{orderId}/invoices")
    public Mono<ApiCommonResponse<?>> queryOrderInvoice(@AuthenticationPrincipal Long userId,
                                                        @PathVariable Long orderId) {
        orderInvoiceService.getInvoice(orderId, userId);
        return Mono.just(ApiCommonResponse.createSuccessResponse());
    }
}
