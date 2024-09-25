package org.example.coin_laundry_app_backend.order.presentation.payload.request;

import org.example.coin_laundry_app_backend.order.application.record.OrderContent;
import org.example.coin_laundry_app_backend.order.application.record.OrderSchedule;

public class CreateOrderRequest {
    OrderContent orderContent;
    Long laundromatId;
    Long addressId;
    OrderSchedule orderSchedule;
}
