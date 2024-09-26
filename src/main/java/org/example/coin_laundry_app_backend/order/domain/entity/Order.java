package org.example.coin_laundry_app_backend.order.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.coin_laundry_app_backend.common.entity.AbstractBaseEntity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Getter
@Table("orders")
@AllArgsConstructor
@NoArgsConstructor
public class Order extends AbstractBaseEntity {

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
    private Long estimatedAmount;
    @CreatedDate
    private String orderedAt;

    public static Order create() {
        return Order.builder()
                .status("sample")
                .customerId(1L)
                .laundromatId(1L)
                .laundromatName("하늘이 세탁소")
                .desiredPickupDatetime("2~")
                .desiredDeliveryDatetime("1~")
                .orderUnitType("SOLO")
                .orderRequestType("NEW")
                .laundryItemType("REGULAR")
                .estimatedAmount(0L)
                .build();
    }
}
