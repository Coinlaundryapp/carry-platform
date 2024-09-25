package org.example.coin_laundry_app_backend.order.domain.model.priceresolver;

import org.example.coin_laundry_app_backend.order.application.record.OptionDescription;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundryItemType;
import org.example.coin_laundry_app_backend.order.domain.enums.option.AdditionalOption;
import org.example.coin_laundry_app_backend.order.domain.enums.order.OrderRequestType;
import org.example.coin_laundry_app_backend.order.domain.enums.order.OrderUnitType;
import org.example.coin_laundry_app_backend.order.domain.model.OptionCondition;

import java.util.HashMap;
import java.util.Map;

public class AdditionalOptionPriceResolver extends AbstractOptionPriceResolver {

    private final Map<OptionCondition, Integer> foldLaundryMap =  new HashMap<>();
    private final Map<OptionCondition, Integer> addSoftenerMap =  new HashMap<>();

    public AdditionalOptionPriceResolver() {
        initializeKey(this.foldLaundryMap);
        initializeKey(this.addSoftenerMap);
    }

    @Override
    public void initializeValue() {
        initializeFoldLaundry();
        initializeAddSoftener();
    }

    private void initializeFoldLaundry() {
        this.foldLaundryMap.put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.REGULAR), 1000);
        // this.foldLaundryMap.put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.BLANKET), 1000);
        this.foldLaundryMap.put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.REGULAR_AND_BLANKET), 1000);
        // this.foldLaundryMap.put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.SHOES), 1000);
    }

    private void initializeAddSoftener() {
        this.addSoftenerMap.put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.REGULAR), 0);
        this.addSoftenerMap.put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.BLANKET), 0);
        this.addSoftenerMap.put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.REGULAR_AND_BLANKET), 0);
        // this.addSoftenerMap .put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.SHOES), 0);
    }

    public OptionDescription resolve(OptionCondition condition, AdditionalOption option) {
        if(option.equals(AdditionalOption.FOLD_LAUNDRY)) {
            Integer price = this.foldLaundryMap.get(condition);
            if(price != null) return new OptionDescription(true, price);
        }
        if(option.equals(AdditionalOption.ADD_SOFTENER)) {
            Integer price = this.addSoftenerMap.get(condition);
            if(price != null) return new OptionDescription(true, price);
        }
        return new OptionDescription(false, null);
    }
}
