package com.carry_laundry.carry_backend.order.domain.enums.option;

import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundrySubOptionType;

public enum WashOption {
    STANDARD,
    HOT_WATER;

    public LaundrySubOptionType of() {
        if(this.equals(STANDARD)) return LaundrySubOptionType.STANDARD;
        if(this.equals(HOT_WATER)) return LaundrySubOptionType.HOT_WATER;
        return null;
    }
}