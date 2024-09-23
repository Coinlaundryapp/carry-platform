package org.example.coin_laundry_app_backend.user.application.record.shipping;

import org.example.coin_laundry_app_backend.user.domain.model.entity.ShippingAddress;

public record ShippingSummary(Long addressId, String addressLabel, String fullAddress,
                              boolean isDefault) {

    public static ShippingSummary of(ShippingAddress shippingAddress) {
        return new ShippingSummary(shippingAddress.getId(), shippingAddress.getAddressLabel(),
            shippingAddress.getBaseAddress() + shippingAddress.getDetailAddress(),
            shippingAddress.getIsDefaultAddress());
    }
}
