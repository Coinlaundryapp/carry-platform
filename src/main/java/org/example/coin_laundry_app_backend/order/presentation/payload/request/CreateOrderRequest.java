package org.example.coin_laundry_app_backend.order.presentation.payload.request;

import lombok.Getter;
import org.example.coin_laundry_app_backend.order.application.record.OrderContent;
import org.example.coin_laundry_app_backend.order.application.record.OrderSchedule;

@Getter
public class CreateOrderRequest {
    private OrderContent orderContent;
    private Long laundromatId;
    private Long addressId;
    private OrderSchedule orderSchedule;
}
