package org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.enums.EntranceType;

@Getter
@Builder
@AllArgsConstructor
public class ShippingAddress {

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

    public static ShippingAddress create(Long userId, String addressLabel, String recipientName, String recipientPhone,
                                         String baseAddress, String detailAddress, String deliveryNotes, EntranceType entranceType, String entranceDetail) {
        return new ShippingAddress(null, userId, null, addressLabel, recipientName, recipientPhone, baseAddress, detailAddress, deliveryNotes, entranceType, entranceDetail);
    }

    public void markAsDefaultAddress() {
        this.isDefaultAddress = true;
    }

    public void clearDefaultAddress() {
        this.isDefaultAddress = false;
    }

    public void overwrite(String addressLabel, String recipientName, String recipientPhone,
                          String baseAddress, String detailAddress, String deliveryNotes, EntranceType entranceType, String entranceDetail) {
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
