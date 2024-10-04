package com.carry_laundry.carry_backend.order.presentation.payload.request;

import com.carry_laundry.carry_backend.order.application.record.OrderContent;
import com.carry_laundry.carry_backend.order.application.record.OrderSchedule;
import lombok.Getter;

@Getter
public class CreateOrderRequest {

    private OrderContent orderContent;
    private Long laundromatId;
    private Long addressId;
    private OrderSchedule orderSchedule;
}
