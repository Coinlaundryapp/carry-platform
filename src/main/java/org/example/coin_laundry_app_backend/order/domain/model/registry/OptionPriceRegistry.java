package org.example.coin_laundry_app_backend.order.domain.model.registry;

import org.example.coin_laundry_app_backend.order.application.record.OptionDescription;
import org.example.coin_laundry_app_backend.order.domain.enums.option.AdditionalOption;
import org.example.coin_laundry_app_backend.order.domain.enums.option.DryOption;
import org.example.coin_laundry_app_backend.order.domain.enums.option.ServiceOptionType;
import org.example.coin_laundry_app_backend.order.domain.enums.option.WashOption;
import org.example.coin_laundry_app_backend.order.domain.model.OptionCondition;
import org.example.coin_laundry_app_backend.order.domain.model.OptionPricePolicy;
import org.example.coin_laundry_app_backend.order.domain.model.optionprice.AdditionalOptionPriceInfo;
import org.example.coin_laundry_app_backend.order.domain.model.optionprice.DryOptionPriceInfo;
import org.example.coin_laundry_app_backend.order.domain.model.optionprice.OptionPriceInfo;
import org.example.coin_laundry_app_backend.order.domain.model.optionprice.WashOptionPriceInfo;
import org.example.coin_laundry_app_backend.order.domain.model.priceresolver.AdditionalOptionPriceResolver;
import org.example.coin_laundry_app_backend.order.domain.model.priceresolver.DryOptionPriceResolver;
import org.example.coin_laundry_app_backend.order.domain.model.priceresolver.WashOptionPriceResolver;

public class OptionPriceRegistry {

    private final WashOptionPriceResolver washOptionPriceResolver;
    private final DryOptionPriceResolver dryOptionPriceResolver;
    private final AdditionalOptionPriceResolver additionalOptionPriceResolver;

    public OptionPriceRegistry() {
        this.washOptionPriceResolver = new WashOptionPriceResolver();
        this.dryOptionPriceResolver = new DryOptionPriceResolver();
        this.additionalOptionPriceResolver = new AdditionalOptionPriceResolver();
    }

    public void mappingWashOptionPrice() {
        this.washOptionPriceResolver.initializeValue();
    }

    public void mappingDryOptionPrice() {
        this.dryOptionPriceResolver.initializeValue();
    }

    public void mappingAdditionalOptionPrice() {
        this.additionalOptionPriceResolver.initializeValue();
    }

    private OptionPriceInfo getWashOptionPriceInfo(OptionCondition condition) {
        return WashOptionPriceInfo.builder()
                .standard(this.washOptionPriceResolver.resolve(condition, WashOption.STANDARD))
                .hotWater(this.washOptionPriceResolver.resolve(condition, WashOption.HOT_WATER))
                .build();
    }

    private OptionPriceInfo getDryOptionPriceInfo(OptionCondition condition) {
        return DryOptionPriceInfo.builder()
                .lowHeat(this.dryOptionPriceResolver.resolve(condition, DryOption.LOW_HEAT))
                .highHeat(this.dryOptionPriceResolver.resolve(condition, DryOption.HIGH_HEAT))
                .build();
    }

    private OptionPriceInfo getAdditionalOptionPriceInfo(OptionCondition condition) {
        return AdditionalOptionPriceInfo.builder()
                .foldLaundry(this.additionalOptionPriceResolver.resolve(condition, AdditionalOption.FOLD_LAUNDRY))
                .addSoftener(this.additionalOptionPriceResolver.resolve(condition, AdditionalOption.ADD_SOFTENER))
                .build();
    }

    public OptionPricePolicy getOptionPricePolicy(OptionCondition condition) {
        return OptionPricePolicy.builder()
                .washOption(getWashOptionPriceInfo(condition))
                .dryOption(getDryOptionPriceInfo(condition))
                .additionalOption(getAdditionalOptionPriceInfo(condition))
                .build();
    }
}
