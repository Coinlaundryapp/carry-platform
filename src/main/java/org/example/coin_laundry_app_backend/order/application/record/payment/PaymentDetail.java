package org.example.coin_laundry_app_backend.order.application.record.payment;

public record PaymentDetail(
        DiscountDetail discounts,
        ChargeDetail charges,
        int netAmount
) {}
