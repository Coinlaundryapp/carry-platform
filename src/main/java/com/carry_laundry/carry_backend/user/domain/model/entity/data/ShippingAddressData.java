package com.carry_laundry.carry_backend.user.domain.model.entity.data;

import com.carry_laundry.carry_backend.user.domain.model.enums.EntranceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Builder
@AllArgsConstructor
@Table("shipping_addresses")
public class ShippingAddressData {

    @Id
    private Long id;
    private Long userId;
    private Boolean isDefaultAddress;
    private String addressLabel;
    private String recipientName;
    private String recipientPhone;

    private String baseAddress;
    private String detailAddress;

    private String deliveryNotes;
    private EntranceType entranceType;
    private String entranceDetail;
}
