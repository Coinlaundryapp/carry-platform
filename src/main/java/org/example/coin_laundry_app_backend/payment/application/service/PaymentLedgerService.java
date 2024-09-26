package org.example.coin_laundry_app_backend.payment.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentLedgerService {

    private final PaymentGatewayService gatewayService;

    public void recordPayment() {

    }

    public void approvePayment() {

    }

}