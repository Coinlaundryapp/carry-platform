package com.carry_laundry.carry_backend.payment.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.payment.application.service.PaymentLedgerService;
import com.carry_laundry.carry_backend.payment.presentation.payload.request.PaymentApprovalRequest;
import lombok.RequiredArgsConstructor;
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

    @PostMapping
    public Mono<ApiCommonResponse<?>> approvePayment(@RequestBody PaymentApprovalRequest request) {
        ledgerService.recordPayment();
        ledgerService.approvePayment();
        return null;
    }
}