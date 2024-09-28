package org.example.coin_laundry_app_backend.order.domain.model.optionprice;

import lombok.Builder;
import lombok.Data;
import org.example.coin_laundry_app_backend.order.application.record.OptionDescription;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundrySubOptionType;
import org.example.coin_laundry_app_backend.order.domain.enums.option.DryOption;
import org.example.coin_laundry_app_backend.order.domain.enums.option.WashOption;

@Data
@Builder
public class DryOptionPriceInfo implements OptionPriceInfo {
    OptionDescription lowHeat;
    OptionDescription highHeat;

    @Override
    public Integer getPrice(LaundrySubOptionType subOptionType) {
        if(subOptionType == null) return 0;
        if(subOptionType.equals(LaundrySubOptionType.LOW_HEAT)) return lowHeat.price();
        if(subOptionType.equals(LaundrySubOptionType.HIGH_HEAT)) return highHeat.price();
        return 0;
    }
}
