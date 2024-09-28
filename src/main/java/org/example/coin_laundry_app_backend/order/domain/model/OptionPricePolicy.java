package org.example.coin_laundry_app_backend.order.domain.model;

import lombok.Builder;
import lombok.Data;
import org.example.coin_laundry_app_backend.order.domain.model.optionprice.OptionPriceInfo;

@Data
@Builder
public class OptionPricePolicy {
    private OptionPriceInfo washOption;
    private OptionPriceInfo dryOption;
    private OptionPriceInfo additionalOption;
}
