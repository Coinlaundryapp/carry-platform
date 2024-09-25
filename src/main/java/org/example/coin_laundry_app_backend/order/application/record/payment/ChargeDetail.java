package org.example.coin_laundry_app_backend.order.application.record.payment;

public record ChargeDetail(
        int laundryPrice,
        int deliveryFee,
        int serviceFee
) {}
