package com.carry_laundry.carry_backend.order.domain.model.optionprice;

import com.carry_laundry.carry_backend.order.application.record.OptionDescription;
import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundrySubOptionType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DryOptionPriceInfo implements OptionPriceInfo {

    OptionDescription lowHeat;
    OptionDescription highHeat;

    @Override
    public Integer getPrice(LaundrySubOptionType subOptionType) {
        if (subOptionType == null) {
            return 0;
        }
        if (subOptionType.equals(LaundrySubOptionType.LOW_HEAT)) {
            return lowHeat.price();
        }
        if (subOptionType.equals(LaundrySubOptionType.HIGH_HEAT)) {
            return highHeat.price();
        }
        return 0;
    }
}
