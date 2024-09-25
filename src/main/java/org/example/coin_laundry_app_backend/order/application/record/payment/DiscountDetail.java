package org.example.coin_laundry_app_backend.order.application.record.payment;

import java.util.List;

public record DiscountDetail(
        List<String> laundryDiscounts,
        List<String> deliveryDiscounts
) {}
