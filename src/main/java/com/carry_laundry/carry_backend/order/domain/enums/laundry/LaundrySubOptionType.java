package com.carry_laundry.carry_backend.order.domain.enums.laundry;

import com.carry_laundry.carry_backend.order.domain.enums.option.AdditionalOption;
import com.carry_laundry.carry_backend.order.domain.enums.option.DryOption;

public enum LaundrySubOptionType {
    STANDARD,
    HOT_WATER,
    LOW_HEAT,
    HIGH_HEAT,
    FOLD_LAUNDRY,
    ADD_SOFTENER;

    public LaundrySubOptionType of(DryOption dryOption) {
        if(dryOption.name().equals(LOW_HEAT.name())) return LOW_HEAT;
        if(dryOption.name().equals(HIGH_HEAT.name())) return HIGH_HEAT;
        return null;
    }

    public LaundrySubOptionType of(AdditionalOption additionalOption) {
        if(additionalOption.name().equals(FOLD_LAUNDRY.name())) return FOLD_LAUNDRY;
        if(additionalOption.name().equals(ADD_SOFTENER.name())) return ADD_SOFTENER;
        return null;
    }
}
