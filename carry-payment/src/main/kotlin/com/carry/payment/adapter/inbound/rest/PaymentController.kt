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
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Payment", description = "결제 관리 API")
@RestController
@RequestMapping("/api/v2/payments")
class PaymentController(
    private val paymentCommandUseCase: PaymentCommandUseCase,
    private val paymentQueryUseCase: PaymentQueryUseCase,
    private val invoiceQueryUseCase: InvoiceQueryUseCase,
) {

    @Operation(
        summary = "결제 요청",
        description = "주문에 대한 결제를 요청합니다. Idempotency-Key 헤더 제공 시 동일 키 재요청은 기존 결과를 재생하며(이중 청구 방지), 진짜 재시도는 새 키를 사용합니다.",
    )
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "201", description = "결제 요청 성공"), SwaggerApiResponse(responseCode = "400", description = "잘못된 요청"), SwaggerApiResponse(responseCode = "404", description = "주문을 찾을 수 없음"), SwaggerApiResponse(responseCode = "409", description = "동일 Idempotency-Key 요청 진행 중")])
    @PostMapping("/pay")
    fun requestPayment(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestParam orderId: Long,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: PaymentRequest,
    ): ResponseEntity<ApiResponse<PaymentResponse>> {
        val command = RequestPaymentCommand(
            orderId = orderId,
            customerId = userId,
            pgProvider = PgProvider.valueOf(request.pgProvider),
            paymentKey = request.paymentKey,
            idempotencyKey = idempotencyKey,
        )
        val payment = paymentCommandUseCase.requestPayment(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(PaymentResponse.from(payment)))
    }

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
