package org.example.coin_laundry_app_backend.order.domain.model.optionprice;

import lombok.Builder;
import lombok.Data;
import org.example.coin_laundry_app_backend.order.application.record.OptionDescription;

@Data
@Builder
public class WashOptionPriceInfo implements OptionPriceInfo {
    OptionDescription standard;
    OptionDescription hotWater;
}
