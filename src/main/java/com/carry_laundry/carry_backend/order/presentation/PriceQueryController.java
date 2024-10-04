package com.carry_laundry.carry_backend.order.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.order.application.service.PriceInquiryService;
import com.carry_laundry.carry_backend.order.domain.model.OptionCondition;
import com.carry_laundry.carry_backend.order.presentation.payload.request.QueryPricePolicyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RequestMapping("/api/v1/prices")
@RestController
@RequiredArgsConstructor
public class PriceQueryController {

    private final PriceInquiryService priceInquiryService;

    @GetMapping
    public Mono<ApiCommonResponse<?>> queryPricePolicy(
        @ModelAttribute @Valid QueryPricePolicyRequest request) {
        return priceInquiryService.getPricePolicy(OptionCondition.of(request))
            .map(ApiCommonResponse::createSuccessResponse);
    }
}
