package com.carry_laundry.carry_backend.order.domain.enums.option;

import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundrySubOptionType;

public enum DryOption {
    LOW_HEAT,
    HIGH_HEAT;

    public LaundrySubOptionType of() {
        if(this.equals(LOW_HEAT)) return LaundrySubOptionType.LOW_HEAT;
        if(this.equals(HIGH_HEAT)) return LaundrySubOptionType.HIGH_HEAT;
        return null;
    }
}