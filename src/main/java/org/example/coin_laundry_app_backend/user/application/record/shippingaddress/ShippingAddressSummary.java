package org.example.coin_laundry_app_backend.user.application.record.shippingaddress;

import org.example.coin_laundry_app_backend.user.domain.entity.ShippingAddress;

public record ShippingAddressSummary(Long addressId, String addressLabel, String fullAddress,
                                     boolean isDefault) {

    public static ShippingAddressSummary of(ShippingAddress shippingAddress) {
        return new ShippingAddressSummary(shippingAddress.getId(), shippingAddress.getAddressLabel(),
            shippingAddress.getBaseAddress() + shippingAddress.getDetailAddress(),
            shippingAddress.getIsDefaultAddress());
    }
}