package org.example.coin_laundry_app_backend.order.domain.model.optionprice;

import lombok.Builder;
import lombok.Data;
import org.example.coin_laundry_app_backend.order.application.record.OptionDescription;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundrySubOptionType;
import org.example.coin_laundry_app_backend.order.domain.enums.option.AdditionalOption;
import org.example.coin_laundry_app_backend.order.domain.enums.option.WashOption;

@Data
@Builder
public class AdditionalOptionPriceInfo implements OptionPriceInfo {
    OptionDescription foldLaundry;
    OptionDescription addSoftener;

    @Override
    public Integer getPrice(LaundrySubOptionType subOptionType) {
        if(subOptionType == null) return 0;
        if(subOptionType.equals(LaundrySubOptionType.FOLD_LAUNDRY)) return foldLaundry.price();
        if(subOptionType.equals(LaundrySubOptionType.ADD_SOFTENER)) return addSoftener.price();
        return 0;
    }
}
