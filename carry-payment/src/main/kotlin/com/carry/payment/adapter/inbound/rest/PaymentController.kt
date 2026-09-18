package com.carry.payment.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.payment.adapter.inbound.rest.dto.InvoiceResponse
import com.carry.payment.adapter.inbound.rest.dto.PaymentResponse
import com.carry.payment.application.port.inbound.InvoiceQueryUseCase
import com.carry.payment.application.port.inbound.PaymentCommandUseCase
import com.carry.payment.application.port.inbound.PaymentQueryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Payment", description = "결제 관리 API")
@RestController
@RequestMapping("/api/v2/payments")
class PaymentController(
    private val paymentCommandUseCase: PaymentCommandUseCase,
    private val paymentQueryUseCase: PaymentQueryUseCase,
    private val invoiceQueryUseCase: InvoiceQueryUseCase,
) {

    @Operation(summary = "청구서 조회", description = "주문의 청구서를 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "청구서 조회 성공"), SwaggerApiResponse(responseCode = "404", description = "청구서를 찾을 수 없음")])
    @GetMapping("/{orderId}/invoice")
    fun getInvoice(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable orderId: Long,
    ): ResponseEntity<ApiResponse<InvoiceResponse>> {
        val invoice = invoiceQueryUseCase.getInvoiceByOrder(orderId, userId)
        return ResponseEntity.ok(ApiResponse.success(InvoiceResponse.from(invoice)))
    }

    @Operation(summary = "결제 정보 조회", description = "주문의 결제 정보를 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "결제 조회 성공"), SwaggerApiResponse(responseCode = "404", description = "결제를 찾을 수 없음")])
    @GetMapping("/{orderId}/payment")
    fun getPayment(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable orderId: Long,
    ): ResponseEntity<ApiResponse<PaymentResponse>> {
        val payment = paymentQueryUseCase.getPaymentByOrder(orderId, userId)
        return ResponseEntity.ok(ApiResponse.success(PaymentResponse.from(payment)))
    }
}
