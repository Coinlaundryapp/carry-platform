package org.example.coin_laundry_app_backend.payment.presentation.payload.request;

import lombok.Getter;

@Getter
public class PaymentApprovalRequest {
    private Long orderId;
    private String paymentKey;
    private Integer amount;
}
