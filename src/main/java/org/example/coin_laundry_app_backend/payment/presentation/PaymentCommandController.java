package org.example.coin_laundry_app_backend.payment.presentation;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.payment.application.service.PaymentGatewayService;
import org.example.coin_laundry_app_backend.payment.application.service.PaymentLedgerService;
import org.example.coin_laundry_app_backend.payment.presentation.payload.request.PaymentApprovalRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.OAuthCodeRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.LoginResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RequestMapping("api/v1/payments")
@RestController
public class PaymentCommandController {

    private final PaymentLedgerService ledgerService;
    private final PaymentGatewayService gatewayService;

    @PostMapping
    public Mono<ApiCommonResponse<?>> approvePayment(@RequestBody PaymentApprovalRequest request) {
        return null;
    }
}