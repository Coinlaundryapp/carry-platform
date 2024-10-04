package com.carry_laundry.carry_backend.order.domain.model.optionprice;

import com.carry_laundry.carry_backend.order.application.record.OptionDescription;
import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundrySubOptionType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdditionalOptionPriceInfo implements OptionPriceInfo {

    OptionDescription foldLaundry;
    OptionDescription addSoftener;

    @Override
    public Integer getPrice(LaundrySubOptionType subOptionType) {
        if (subOptionType == null) {
            return 0;
        }
        if (subOptionType.equals(LaundrySubOptionType.FOLD_LAUNDRY)) {
            return foldLaundry.price();
        }
        if (subOptionType.equals(LaundrySubOptionType.ADD_SOFTENER)) {
            return addSoftener.price();
        }
        return 0;
    }
}
