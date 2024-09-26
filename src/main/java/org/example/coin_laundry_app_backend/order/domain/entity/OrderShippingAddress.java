package org.example.coin_laundry_app_backend.order.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.coin_laundry_app_backend.common.entity.AbstractBaseEntity;
import org.example.coin_laundry_app_backend.user.domain.enums.EntranceType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("order_shipping_addresses")
@AllArgsConstructor
public class OrderShippingAddress extends AbstractBaseEntity {

    @Id
    private Long id;
    private Long orderId;
    private String addressLabel;
    private String recipientName;
    private String recipientPhone;
    private String baseAddress;
    private String detailAddress;
    private Double latitude;
    private Double longitude;
    private String deliveryNotes;
    private String entranceType;
    private String entranceDetail;
}
