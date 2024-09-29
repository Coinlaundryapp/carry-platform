package com.carry_laundry.carry_backend.order.domain.enums.option;

import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundrySubOptionType;

public enum AdditionalOption {
    FOLD_LAUNDRY,
    ADD_SOFTENER;

    public LaundrySubOptionType of() {
        if(this.equals(FOLD_LAUNDRY)) return LaundrySubOptionType.FOLD_LAUNDRY;
        if(this.equals(ADD_SOFTENER)) return LaundrySubOptionType.ADD_SOFTENER;
        return null;
    }
}