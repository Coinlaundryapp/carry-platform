package com.carry.payment.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.payment.adapter.inbound.rest.dto.InvoiceResponse
import com.carry.payment.adapter.inbound.rest.dto.PaymentRequest
import com.carry.payment.adapter.inbound.rest.dto.PaymentResponse
import com.carry.payment.application.port.inbound.InvoiceQueryUseCase
import com.carry.payment.application.port.inbound.PaymentCommandUseCase
import com.carry.payment.application.port.inbound.PaymentQueryUseCase
import com.carry.payment.application.port.inbound.RequestPaymentCommand
import com.carry.payment.domain.vo.PgProvider
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/payments")
class PaymentController(
    private val paymentCommandUseCase: PaymentCommandUseCase,
    private val paymentQueryUseCase: PaymentQueryUseCase,
    private val invoiceQueryUseCase: InvoiceQueryUseCase,
) {

    @PostMapping("/pay")
    fun requestPayment(
        @AuthenticationPrincipal userId: Long,
        @RequestParam orderId: Long,
        @Valid @RequestBody request: PaymentRequest,
    ): ResponseEntity<ApiResponse<PaymentResponse>> {
        val command = RequestPaymentCommand(
            orderId = orderId,
            customerId = userId,
            pgProvider = PgProvider.valueOf(request.pgProvider),
            paymentKey = request.paymentKey,
        )
        val payment = paymentCommandUseCase.requestPayment(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(PaymentResponse.from(payment)))
    }

    @GetMapping("/{orderId}/invoice")
    fun getInvoice(@PathVariable orderId: Long): ResponseEntity<ApiResponse<InvoiceResponse>> {
        val invoice = invoiceQueryUseCase.getInvoiceByOrder(orderId)
        return ResponseEntity.ok(ApiResponse.success(InvoiceResponse.from(invoice)))
    }

    @GetMapping("/{orderId}/payment")
    fun getPayment(@PathVariable orderId: Long): ResponseEntity<ApiResponse<PaymentResponse>> {
        val payment = paymentQueryUseCase.getPaymentByOrder(orderId)
        return ResponseEntity.ok(ApiResponse.success(PaymentResponse.from(payment)))
    }
}
