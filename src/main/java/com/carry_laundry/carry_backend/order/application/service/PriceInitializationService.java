package com.carry_laundry.carry_backend.order.application.service;

import com.carry_laundry.carry_backend.order.domain.model.registry.OptionPriceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class PriceInitializationService {

    // TODO Load Data From Datasource

    public PriceInitializationService() {
    }

    @Bean
    OptionPriceRegistry configurePriceRegistry() {
        loadPricePolicyData();
        OptionPriceRegistry registry = new OptionPriceRegistry();
        registry.mappingWashOptionPrice();
        registry.mappingDryOptionPrice();
        registry.mappingAdditionalOptionPrice();
        return registry;
    }

    private void loadPricePolicyData() {
        // TODO Prepare Policy from Repository
    }

}
