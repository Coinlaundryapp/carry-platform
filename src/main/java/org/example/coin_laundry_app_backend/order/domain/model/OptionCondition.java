package org.example.coin_laundry_app_backend.order.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundryItemType;
import org.example.coin_laundry_app_backend.order.domain.enums.order.OrderRequestType;
import org.example.coin_laundry_app_backend.order.domain.enums.order.OrderUnitType;
import org.example.coin_laundry_app_backend.order.presentation.payload.request.QueryPricePolicyRequest;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Getter
@Service
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class OptionCondition {
    private OrderUnitType orderUnitType;
    private OrderRequestType orderRequestType;
    private LaundryItemType laundryItemType;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OptionCondition that = (OptionCondition) o;
        return orderUnitType == that.orderUnitType &&
                orderRequestType == that.orderRequestType &&
                laundryItemType == that.laundryItemType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderUnitType, orderRequestType, laundryItemType);
    }

    public static OptionCondition of(QueryPricePolicyRequest request) {
        return new OptionCondition(request.getOrderUnitType(), request.getOrderRequestType(), request.getLaundryItemType());
    }
}
