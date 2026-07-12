package com.carry.payment.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.payment.adapter.inbound.rest.dto.BillingKeyRegisterRequest
import com.carry.payment.adapter.inbound.rest.dto.BillingKeyResponse
import com.carry.payment.application.port.inbound.BillingKeyUseCase
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Billing Key", description = "자동결제 수단(빌링키) 관리 API")
@RestController
@RequestMapping("/api/v2/billing-keys")
class BillingKeyController(
    private val billingKeyUseCase: BillingKeyUseCase,
) {

    @Operation(
        summary = "빌링키 등록",
        description = "PG SDK 카드 등록창 결과(authKey)로 자동결제 수단을 등록합니다. " +
            "이미 등록된 카드가 있으면 기존 키를 무효화하고 새 키로 교체합니다.",
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "빌링키 등록 성공"),
            SwaggerApiResponse(responseCode = "400", description = "PG 발급 거절 또는 잘못된 요청"),
            SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        ],
    )
    @PostMapping
    fun register(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: BillingKeyRegisterRequest,
    ): ResponseEntity<ApiResponse<BillingKeyResponse>> {
        val billingKey = billingKeyUseCase.register(userId, request.authKey)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(BillingKeyResponse.from(billingKey)))
    }

    @Operation(summary = "내 빌링키 조회", description = "현재 등록된 활성 빌링키를 조회합니다")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "빌링키 조회 성공"),
            SwaggerApiResponse(responseCode = "404", description = "등록된 빌링키 없음"),
        ],
    )
    @GetMapping("/me")
    fun getActive(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<BillingKeyResponse>> {
        val billingKey = billingKeyUseCase.getActive(userId)
        return ResponseEntity.ok(ApiResponse.success(BillingKeyResponse.from(billingKey)))
    }
}
