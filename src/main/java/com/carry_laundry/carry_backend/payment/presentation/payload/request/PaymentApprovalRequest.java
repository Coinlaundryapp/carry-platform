package com.carry_laundry.carry_backend.payment.presentation.payload.request;

import lombok.Getter;

@Getter
public class PaymentApprovalRequest {
    private Long orderId;
    private String paymentKey;
    private Integer amount;
}
