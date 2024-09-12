package com.carry_laundry.carry_backend.user.domain.converter;

import com.carry_laundry.carry_backend.user.domain.model.entity.data.ShippingAddressData;
import com.carry_laundry.carry_backend.user.domain.model.entity.domainmodel.ShippingAddress;

public class ShippingAddressConverter {

    public static ShippingAddress toDomain(ShippingAddressData shippingAddressData) {
        return ShippingAddress.builder()
                .id(shippingAddressData.getId())
                .userId(shippingAddressData.getUserId())
                .isDefaultAddress(shippingAddressData.getIsDefaultAddress())
                .addressLabel(shippingAddressData.getAddressLabel())
                .recipientName(shippingAddressData.getRecipientName())
                .recipientPhone(shippingAddressData.getRecipientPhone())
                .baseAddress(shippingAddressData.getBaseAddress())
                .detailAddress(shippingAddressData.getDetailAddress())
                .deliveryNotes(shippingAddressData.getDeliveryNotes())
                .entranceType(shippingAddressData.getEntranceType())
                .entranceDetail(shippingAddressData.getEntranceDetail())
                .build();
    }

    public static ShippingAddressData toData(ShippingAddress shippingAddress) {
        return ShippingAddressData.builder()
                .id(shippingAddress.getId())
                .userId(shippingAddress.getUserId())
                .isDefaultAddress(shippingAddress.getIsDefaultAddress())
                .addressLabel(shippingAddress.getAddressLabel())
                .recipientName(shippingAddress.getRecipientName())
                .recipientPhone(shippingAddress.getRecipientPhone())
                .baseAddress(shippingAddress.getBaseAddress())
                .detailAddress(shippingAddress.getDetailAddress())
                .deliveryNotes(shippingAddress.getDeliveryNotes())
                .entranceType(shippingAddress.getEntranceType())
                .entranceDetail(shippingAddress.getEntranceDetail())
                .build();
    }
}