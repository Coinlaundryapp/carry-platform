package com.carry_laundry.carry_backend.order.domain.model.priceresolver;

import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundryItemType;
import com.carry_laundry.carry_backend.order.domain.enums.order.OrderRequestType;
import com.carry_laundry.carry_backend.order.domain.enums.order.OrderUnitType;
import com.carry_laundry.carry_backend.order.domain.model.OptionCondition;
import java.util.Map;
import lombok.Getter;

@Getter
abstract public class AbstractOptionPriceResolver {

    protected void initializeKey(Map<OptionCondition, Integer> conditionMap) {
        for (OrderUnitType orderUnitType : OrderUnitType.values()) {
            for (OrderRequestType orderRequestType : OrderRequestType.values()) {
                for (LaundryItemType laundryItemType : LaundryItemType.values()) {
                    conditionMap.put(
                        new OptionCondition(orderUnitType, orderRequestType, laundryItemType),
                        null);
                }
            }
        }
    }

    abstract public void initializeValue();
}
