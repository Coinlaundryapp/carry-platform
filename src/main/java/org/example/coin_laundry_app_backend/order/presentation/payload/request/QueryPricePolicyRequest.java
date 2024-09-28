package org.example.coin_laundry_app_backend.order.presentation.payload.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundryItemType;
import org.example.coin_laundry_app_backend.order.domain.enums.order.OrderRequestType;
import org.example.coin_laundry_app_backend.order.domain.enums.order.OrderUnitType;
import org.springframework.web.bind.annotation.BindParam;

@Getter
public class QueryPricePolicyRequest {

    @NotNull
    private OrderUnitType orderUnitType;
    @NotNull
    private OrderRequestType orderRequestType;
    @NotNull
    private LaundryItemType laundryItemType;

    QueryPricePolicyRequest(@BindParam("orderUnitType") OrderUnitType orderUnitType,
                            @BindParam("orderRequestType") OrderRequestType orderRequestType,
                            @BindParam("laundryItemType") LaundryItemType laundryItemType) {
        this.orderUnitType = orderUnitType;
        this.orderRequestType = orderRequestType;
        this.laundryItemType = laundryItemType;
    }
}
