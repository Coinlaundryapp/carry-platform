package org.example.coin_laundry_app_backend.order.domain.entity;

import lombok.*;
import org.example.coin_laundry_app_backend.order.application.record.OrderContent;
import org.example.coin_laundry_app_backend.order.application.record.OrderSchedule;
import org.example.coin_laundry_app_backend.order.domain.enums.orderdetail.OrderDetailStatus;
import org.example.coin_laundry_app_backend.order.presentation.payload.request.CreateOrderRequest;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
@Setter
@Table("orders")
@AllArgsConstructor
@NoArgsConstructor
public class Order {

    @Id
    private Long id;
    private String status;
    private Long customerId;
    private String orderUnitType;
    private String orderRequestType;
    private String laundryItemType;
    private Long laundromatId;
    private String laundromatName;
    private String desiredPickupDatetime;
    private String desiredDeliveryDatetime;
    @CreatedDate
    private String orderedAt;
    @CreatedDate
    private String createdAt;
    @LastModifiedDate
    private String updatedAt;
    @Transient
    private Invoice invoice;
    @Transient
    private OrderShippingAddress orderShippingAddress;
    @Transient
    private List<OrderOption> orderOptions = new ArrayList<>();
    @Transient
    private List<OrderSpecification> orderSpecifications = new ArrayList<>();

    public static Order create(CreateOrderRequest request, Long customerId, String laundromatName) {
        OrderContent orderContent = request.getOrderContent();
        OrderSchedule orderSchedule = request.getOrderSchedule();
        return Order.builder()
                .status(String.valueOf(OrderDetailStatus.ORDER_COMPLETED))
                .customerId(customerId)
                .orderUnitType(String.valueOf(orderContent.orderUnitType()))
                .orderRequestType(String.valueOf(orderContent.orderRequestType()))
                .laundryItemType(String.valueOf(orderContent.laundryItemType()))
                .laundromatId(request.getLaundromatId())
                .laundromatName(laundromatName)
                .desiredPickupDatetime(orderSchedule.desiredPickupDateTime())
                .desiredDeliveryDatetime(orderSchedule.desiredDeliveryDateTime())
                .build();
    }
}
