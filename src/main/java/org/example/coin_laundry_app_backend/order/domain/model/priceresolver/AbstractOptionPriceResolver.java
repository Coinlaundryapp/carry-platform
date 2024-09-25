package org.example.coin_laundry_app_backend.order.domain.model.priceresolver;

import lombok.Getter;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundryItemType;
import org.example.coin_laundry_app_backend.order.domain.enums.order.OrderRequestType;
import org.example.coin_laundry_app_backend.order.domain.enums.order.OrderUnitType;
import org.example.coin_laundry_app_backend.order.domain.model.OptionCondition;

import java.util.Map;

@Getter
abstract public class AbstractOptionPriceResolver {

    protected void initializeKey(Map<OptionCondition, Integer> conditionMap) {
        for (OrderUnitType orderUnitType : OrderUnitType.values()) {
            for (OrderRequestType orderRequestType : OrderRequestType.values()) {
                for (LaundryItemType laundryItemType : LaundryItemType.values()) {
                    conditionMap.put(new OptionCondition(orderUnitType, orderRequestType, laundryItemType), null);
                }
            }
        }
    }

    abstract public void initializeValue();
}
