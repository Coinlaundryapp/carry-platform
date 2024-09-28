package org.example.coin_laundry_app_backend.order.domain.model.optionprice;

import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundrySubOptionType;

public interface OptionPriceInfo {
    Integer getPrice(LaundrySubOptionType subOptionType);
}