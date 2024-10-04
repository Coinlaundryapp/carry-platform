package com.carry_laundry.carry_backend.order.domain.model;

import com.carry_laundry.carry_backend.order.domain.model.optionprice.OptionPriceInfo;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OptionPricePolicy {

    private OptionPriceInfo washOption;
    private OptionPriceInfo dryOption;
    private OptionPriceInfo additionalOption;
}
