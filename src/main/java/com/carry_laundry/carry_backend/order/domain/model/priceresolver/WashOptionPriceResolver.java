package com.carry_laundry.carry_backend.order.domain.model.priceresolver;

import com.carry_laundry.carry_backend.order.application.record.OptionDescription;
import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundryItemType;
import com.carry_laundry.carry_backend.order.domain.enums.option.WashOption;
import com.carry_laundry.carry_backend.order.domain.enums.order.OrderRequestType;
import com.carry_laundry.carry_backend.order.domain.enums.order.OrderUnitType;
import com.carry_laundry.carry_backend.order.domain.model.OptionCondition;
import java.util.HashMap;
import java.util.Map;

public class WashOptionPriceResolver extends AbstractOptionPriceResolver {

    private final Map<OptionCondition, Integer> standardPriceMap = new HashMap<>();
    private final Map<OptionCondition, Integer> hotWaterPriceMap = new HashMap<>();

    public WashOptionPriceResolver() {
        initializeKey(this.standardPriceMap);
        initializeKey(this.hotWaterPriceMap);
    }

    // FIXME
    @Override
    public void initializeValue() {
        initializeStandard();
        initializeHotWater();
    }

    private void initializeStandard() {
        this.standardPriceMap.put(
            new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.REGULAR),
            4500);
        this.standardPriceMap.put(
            new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.BLANKET),
            4500);
        this.standardPriceMap.put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW,
            LaundryItemType.REGULAR_AND_BLANKET), 4500);
        this.standardPriceMap.put(
            new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.SHOES),
            4500);
    }

    private void initializeHotWater() {
        this.hotWaterPriceMap.put(
            new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.REGULAR),
            5000);
        this.hotWaterPriceMap.put(
            new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.BLANKET),
            5000);
        this.hotWaterPriceMap.put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW,
            LaundryItemType.REGULAR_AND_BLANKET), 5000);
        // this.hotWaterPriceMap .put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.SHOES), 5000);
    }

    public OptionDescription resolve(OptionCondition condition, WashOption option) {
        if (option.equals(WashOption.STANDARD)) {
            Integer price = this.standardPriceMap.get(condition);
            if (price != null) {
                return new OptionDescription(true, price);
            }
        }
        if (option.equals(WashOption.HOT_WATER)) {
            Integer price = this.hotWaterPriceMap.get(condition);
            if (price != null) {
                return new OptionDescription(true, price);
            }
        }
        return new OptionDescription(false, null);
    }
}
