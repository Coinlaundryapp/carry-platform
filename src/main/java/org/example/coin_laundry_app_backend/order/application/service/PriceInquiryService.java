package org.example.coin_laundry_app_backend.order.application.service;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.order.application.record.OptionDescription;
import org.example.coin_laundry_app_backend.order.domain.model.OptionCondition;
import org.example.coin_laundry_app_backend.order.domain.model.OptionPricePolicy;
import org.example.coin_laundry_app_backend.order.domain.model.optionprice.OptionPriceInfo;
import org.example.coin_laundry_app_backend.order.domain.model.registry.OptionPriceRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PriceInquiryService {

    private final OptionPriceRegistry optionPriceRegistry;

    public Mono<OptionPricePolicy> getPricePolicy(OptionCondition condition) {
        OptionPricePolicy pricePolicy = optionPriceRegistry.getOptionPricePolicy(condition);
        return Mono.just(pricePolicy);
    }
}