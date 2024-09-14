package org.example.coin_laundry_app_backend.user.domain.model.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.coin_laundry_app_backend.user.domain.model.enums.EntranceType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("shipping_addresses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShippingAddress {

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

    public void markAsDefaultAddress() {
        this.isDefaultAddress = true;
    }

    public void overwrite(String addressLabel, String recipientName, String recipientPhone,
        String baseAddress, String detailAddress, String deliveryNotes, EntranceType entranceType,
        String entranceDetail) {
        this.addressLabel = addressLabel;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.baseAddress = baseAddress;
        this.detailAddress = detailAddress;
        this.deliveryNotes = deliveryNotes;
        this.entranceType = entranceType;
        this.entranceDetail = entranceDetail;
    }
}
