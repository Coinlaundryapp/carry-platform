package com.carry_laundry.carry_backend.order.application.record.invoice;


import java.util.List;

// TODO Abstract Discounts
public record DiscountDetail(
        List<Object> laundryDiscounts,
        List<Object> deliveryDiscounts
) {
    public DiscountDetail {
        laundryDiscounts = laundryDiscounts == null ? List.of() : List.copyOf(laundryDiscounts);
        deliveryDiscounts = deliveryDiscounts == null ? List.of() : List.copyOf(deliveryDiscounts);
    }

    public int getDiscountAmount() {
        return 0;
    }
}
