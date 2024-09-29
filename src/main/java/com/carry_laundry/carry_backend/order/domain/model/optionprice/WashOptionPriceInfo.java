package com.carry_laundry.carry_backend.order.domain.model.optionprice;

import com.carry_laundry.carry_backend.order.application.record.OptionDescription;
import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundrySubOptionType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WashOptionPriceInfo implements OptionPriceInfo {

    OptionDescription standard;
    OptionDescription hotWater;

    @Override
    public Integer getPrice(LaundrySubOptionType subOptionType) {
        if (subOptionType == null) {
            return 0;
        }
        if (subOptionType.equals(LaundrySubOptionType.STANDARD)) {
            return standard.price();
        }
        if (subOptionType.equals(LaundrySubOptionType.HOT_WATER)) {
            return hotWater.price();
        }
        return 0;
    }

//    public Integer getPrice(Sub option) {
//        if(option == null) return 0;
//        if(option.equals(WashOption.STANDARD)) return standard.price();
//        if(option.equals(WashOption.HOT_WATER)) return hotWater.price();
//        return null;
//    }
}
