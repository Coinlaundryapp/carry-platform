package com.carry_laundry.carry_backend.order.domain.model.optionprice;

import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundrySubOptionType;

public interface OptionPriceInfo {

    Integer getPrice(LaundrySubOptionType subOptionType);
}