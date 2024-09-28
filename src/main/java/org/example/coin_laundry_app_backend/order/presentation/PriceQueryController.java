package org.example.coin_laundry_app_backend.order.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.order.application.service.PriceInquiryService;
import org.example.coin_laundry_app_backend.order.domain.model.OptionCondition;
import org.example.coin_laundry_app_backend.order.presentation.payload.request.QueryPricePolicyRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RequestMapping("/api/v1/prices")
@RestController
@RequiredArgsConstructor
public class PriceQueryController {

    private final PriceInquiryService priceInquiryService;

    @GetMapping
    public Mono<ApiCommonResponse<?>> queryPricePolicy(@ModelAttribute @Valid QueryPricePolicyRequest request) {
        return priceInquiryService.getPricePolicy(OptionCondition.of(request)).map(ApiCommonResponse::createSuccessResponse);
    }
}
