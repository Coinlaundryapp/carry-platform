package com.carry_laundry.carry_backend.order.domain.entity;

import com.carry_laundry.carry_backend.user.domain.entity.ShippingAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Getter
@Table("order_shipping_addresses")
@AllArgsConstructor
@NoArgsConstructor
public class OrderShippingAddress {

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
    @CreatedDate
    private String createdAt;
    @LastModifiedDate
    private String updatedAt;

    public static OrderShippingAddress create(Order order, ShippingAddress shippingAddress) {
        return OrderShippingAddress.builder()
            .orderId(order.getId())
            .addressLabel(shippingAddress.getAddressLabel())
            .recipientName(shippingAddress.getRecipientName())
            .recipientPhone(shippingAddress.getRecipientPhone())
            .baseAddress(shippingAddress.getBaseAddress())
            .detailAddress(shippingAddress.getDetailAddress())
            .latitude(shippingAddress.getLatitude())
            .longitude(shippingAddress.getLongitude())
            .deliveryNotes(shippingAddress.getDeliveryNotes())
            .entranceType(shippingAddress.getEntranceType().name())
            .entranceDetail(shippingAddress.getEntranceDetail())
            .build();
    }
}
