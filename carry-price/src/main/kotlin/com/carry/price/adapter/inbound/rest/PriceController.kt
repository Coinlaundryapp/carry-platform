package com.carry.price.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.price.adapter.inbound.rest.dto.CalculateTotalRequest
import com.carry.price.adapter.inbound.rest.dto.CalculateTotalResponse
import com.carry.price.adapter.inbound.rest.dto.CreatePricePolicyRequest
import com.carry.price.adapter.inbound.rest.dto.PricePolicyResponse
import com.carry.price.adapter.inbound.rest.dto.UpdateOptionPricesRequest
import com.carry.price.application.port.inbound.PriceCommandUseCase
import com.carry.price.application.port.inbound.PriceQueryUseCase
import com.carry.price.domain.vo.LaundryItemType
import com.carry.price.domain.vo.OrderRequestType
import com.carry.price.domain.vo.OrderUnitType
import com.carry.price.domain.vo.PriceCondition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Price", description = "가격 정책 관리 API")
@RestController
@RequestMapping("/api/v2/prices")
class PriceController(
    private val priceQueryUseCase: PriceQueryUseCase,
    private val priceCommandUseCase: PriceCommandUseCase,
) {

    @Operation(summary = "가격 정책 조회", description = "조건에 맞는 가격 정책을 조회합니다")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "가격 정책 조회 성공"),
            SwaggerApiResponse(responseCode = "404", description = "가격 정책을 찾을 수 없음"),
        ],
    )
    @GetMapping
    fun getPolicy(
        @RequestParam orderUnitType: OrderUnitType,
        @RequestParam orderRequestType: OrderRequestType,
        @RequestParam laundryItemType: LaundryItemType,
    ): ResponseEntity<ApiResponse<PricePolicyResponse>> {
        val condition = PriceCondition(orderUnitType, orderRequestType, laundryItemType)
        val policy = priceQueryUseCase.getPolicyByCondition(condition)
        return ResponseEntity.ok(ApiResponse.success(PricePolicyResponse.from(policy)))
    }

    @Operation(summary = "총 금액 계산", description = "선택한 옵션에 대한 총 금액을 계산합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "금액 계산 성공")])
    @PostMapping("/calculate")
    fun calculateTotal(
        @RequestParam orderUnitType: OrderUnitType,
        @RequestParam orderRequestType: OrderRequestType,
        @RequestParam laundryItemType: LaundryItemType,
        @Valid @RequestBody request: CalculateTotalRequest,
    ): ResponseEntity<ApiResponse<CalculateTotalResponse>> {
        val condition = PriceCondition(orderUnitType, orderRequestType, laundryItemType)
        val options = request.selectedOptions.map { it.optionType to it.subOptionType }
        val total = priceQueryUseCase.calculateTotal(condition, options)
        return ResponseEntity.ok(ApiResponse.success(CalculateTotalResponse(total)))
    }

    @Operation(summary = "가격 정책 생성")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "가격 정책 생성 성공"),
            SwaggerApiResponse(responseCode = "409", description = "이미 존재하는 가격 정책"),
        ],
    )
    @PostMapping
    fun createPolicy(
        @Valid @RequestBody request: CreatePricePolicyRequest,
    ): ResponseEntity<ApiResponse<PricePolicyResponse>> {
        val condition = PriceCondition(
            OrderUnitType.valueOf(request.orderUnitType),
            OrderRequestType.valueOf(request.orderRequestType),
            LaundryItemType.valueOf(request.laundryItemType),
        )
        val policy = priceCommandUseCase.createPolicy(
            condition,
            request.optionPrices.map { it.toDomain() },
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(PricePolicyResponse.from(policy)))
    }

    @Operation(summary = "옵션 가격 수정")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "옵션 가격 수정 성공"),
            SwaggerApiResponse(responseCode = "404", description = "가격 정책을 찾을 수 없음"),
        ],
    )
    @PutMapping("/{policyId}/options")
    fun updateOptionPrices(
        @PathVariable policyId: Long,
        @Valid @RequestBody request: UpdateOptionPricesRequest,
    ): ResponseEntity<ApiResponse<PricePolicyResponse>> {
        val policy = priceCommandUseCase.updateOptionPrices(
            policyId,
            request.optionPrices.map { it.toDomain() },
        )
        return ResponseEntity.ok(ApiResponse.success(PricePolicyResponse.from(policy)))
    }

    @Operation(summary = "가격 정책 삭제")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "204", description = "가격 정책 삭제 성공")])
    @DeleteMapping("/{policyId}")
    fun deletePolicy(
        @PathVariable policyId: Long,
    ): ResponseEntity<Void> {
        priceCommandUseCase.deletePolicy(policyId)
        return ResponseEntity.noContent().build()
    }
}
