package org.example.coin_laundry_app_backend.order.application.record;

import org.example.coin_laundry_app_backend.order.domain.entity.OrderShippingAddress;

public record ShippingAddressView(String addressLabel, String recipientPhone, String recipientName,
                                  String baseAddress, String detailAddress, String deliveryNotes,
                                  String entranceType, String entranceDetail) {

    public static ShippingAddressView of(OrderShippingAddress orderShippingAddress) {
        return new ShippingAddressView(
                orderShippingAddress.getAddressLabel(),
                orderShippingAddress.getRecipientPhone(),
                orderShippingAddress.getRecipientName(),
                orderShippingAddress.getBaseAddress(),
                orderShippingAddress.getDetailAddress(),
                orderShippingAddress.getDeliveryNotes(),
                orderShippingAddress.getEntranceType(),
                orderShippingAddress.getEntranceDetail()
        );
    }
}
