package com.carry_laundry.carry_backend.order.domain.model.priceresolver;

import com.carry_laundry.carry_backend.order.application.record.OptionDescription;
import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundryItemType;
import com.carry_laundry.carry_backend.order.domain.enums.option.DryOption;
import com.carry_laundry.carry_backend.order.domain.enums.order.OrderRequestType;
import com.carry_laundry.carry_backend.order.domain.enums.order.OrderUnitType;
import com.carry_laundry.carry_backend.order.domain.model.OptionCondition;
import java.util.HashMap;
import java.util.Map;

public class DryOptionPriceResolver extends AbstractOptionPriceResolver {

    private final Map<OptionCondition, Integer> lowHeatMap = new HashMap<>();
    private final Map<OptionCondition, Integer> highHeatMap = new HashMap<>();

    public DryOptionPriceResolver() {
        initializeKey(this.lowHeatMap);
        initializeKey(this.highHeatMap);
    }

    @Override
    public void initializeValue() {
        initializeLowHeat();
        initializeHighHeat();
    }

    private void initializeLowHeat() {
        this.lowHeatMap.put(
            new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.REGULAR),
            4000);
        this.lowHeatMap.put(
            new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.BLANKET),
            4000);
        this.lowHeatMap.put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW,
            LaundryItemType.REGULAR_AND_BLANKET), 4000);
        // this.lowHeatMap.put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.SHOES), 4000);
    }

    private void initializeHighHeat() {
        this.highHeatMap.put(
            new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.REGULAR),
            4000);
        this.highHeatMap.put(
            new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.BLANKET),
            4000);
        this.highHeatMap.put(new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW,
            LaundryItemType.REGULAR_AND_BLANKET), 4000);
        this.highHeatMap.put(
            new OptionCondition(OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.SHOES),
            4000);
    }

    public OptionDescription resolve(OptionCondition condition, DryOption option) {
        if (option.equals(DryOption.LOW_HEAT)) {
            Integer price = this.lowHeatMap.get(condition);
            if (price != null) {
                return new OptionDescription(true, price);
            }
        }
        if (option.equals(DryOption.HIGH_HEAT)) {
            Integer price = this.highHeatMap.get(condition);
            if (price != null) {
                return new OptionDescription(true, price);
            }
        }
        return new OptionDescription(false, null);
    }
}
