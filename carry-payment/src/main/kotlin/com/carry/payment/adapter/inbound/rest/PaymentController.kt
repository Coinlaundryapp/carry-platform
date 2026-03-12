package com.carry.payment.adapter.inbound.rest

import com.carry.payment.adapter.inbound.rest.dto.InvoiceResponse
import com.carry.payment.adapter.inbound.rest.dto.PaymentRequest
import com.carry.payment.adapter.inbound.rest.dto.PaymentResponse
import com.carry.payment.application.port.inbound.InvoiceQueryUseCase
import com.carry.payment.application.port.inbound.PaymentCommandUseCase
import com.carry.payment.application.port.inbound.PaymentQueryUseCase
import com.carry.payment.application.port.inbound.RequestPaymentCommand
import com.carry.payment.domain.vo.PgProvider
import org.springframework.http.ResponseEntity
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
        @RequestParam customerId: Long, // TODO: JWT에서 추출
        @RequestParam orderId: Long,
        @RequestBody request: PaymentRequest,
    ): ResponseEntity<PaymentResponse> {
        val command = RequestPaymentCommand(
            orderId = orderId,
            customerId = customerId,
            pgProvider = PgProvider.valueOf(request.pgProvider),
            paymentKey = request.paymentKey,
        )
        val payment = paymentCommandUseCase.requestPayment(command)
        return ResponseEntity.ok(PaymentResponse.from(payment))
    }

    @GetMapping("/{orderId}/invoice")
    fun getInvoice(@PathVariable orderId: Long): ResponseEntity<InvoiceResponse> {
        val invoice = invoiceQueryUseCase.getInvoiceByOrder(orderId)
        return ResponseEntity.ok(InvoiceResponse.from(invoice))
    }

    @GetMapping("/{orderId}/payment")
    fun getPayment(@PathVariable orderId: Long): ResponseEntity<PaymentResponse> {
        val payment = paymentQueryUseCase.getPaymentByOrder(orderId)
        return ResponseEntity.ok(PaymentResponse.from(payment))
    }
}
