package com.carry_laundry.carry_backend.order.presentation.payload.response;

import com.carry_laundry.carry_backend.order.domain.entity.Invoice;
import com.carry_laundry.carry_backend.order.domain.entity.Order;
import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundryItemType;
import com.carry_laundry.carry_backend.order.domain.enums.order.OrderRequestType;
import com.carry_laundry.carry_backend.order.domain.enums.order.OrderUnitType;
import com.carry_laundry.carry_backend.order.domain.enums.orderdetail.OrderDetailStatus;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Builder;
import lombok.Data;
import reactor.util.function.Tuple2;

@Builder
@Data
public class CreateOrderResponse {

    private Long id;
    private OrderDetailStatus status;
    private OrderUnitType orderUnitType;
    private OrderRequestType orderRequestType;
    private LaundryItemType laundryItemType;
    private String laundromatName;
    private String orderedAt;
    private Integer estimatedAmount;

    public static CreateOrderResponse create(Tuple2<Order, Invoice> tuple2) {
        Order order = tuple2.getT1();
        Invoice invoice = tuple2.getT2();
        // FIXME
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        String formattedTime = now.format(formatter);

        return CreateOrderResponse.builder()
            .id(order.getId())
            .status(OrderDetailStatus.valueOf(order.getStatus()))
            .orderUnitType(OrderUnitType.valueOf(order.getOrderUnitType()))
            .orderRequestType(OrderRequestType.valueOf(order.getOrderRequestType()))
            .laundryItemType(LaundryItemType.valueOf(order.getLaundryItemType()))
            .laundromatName(order.getLaundromatName())
            .orderedAt(formattedTime)
            .estimatedAmount(invoice.getNetAmount())
            .build();
    }
}
