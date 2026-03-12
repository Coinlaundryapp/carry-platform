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

@RestController
@RequestMapping("/api/v1/prices")
class PriceController(
    private val priceQueryUseCase: PriceQueryUseCase,
    private val priceCommandUseCase: PriceCommandUseCase,
) {

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

    @PostMapping("/calculate")
    fun calculateTotal(
        @RequestParam orderUnitType: OrderUnitType,
        @RequestParam orderRequestType: OrderRequestType,
        @RequestParam laundryItemType: LaundryItemType,
        @RequestBody request: CalculateTotalRequest,
    ): ResponseEntity<ApiResponse<CalculateTotalResponse>> {
        val condition = PriceCondition(orderUnitType, orderRequestType, laundryItemType)
        val options = request.selectedOptions.map { it.optionType to it.subOptionType }
        val total = priceQueryUseCase.calculateTotal(condition, options)
        return ResponseEntity.ok(ApiResponse.success(CalculateTotalResponse(total)))
    }

    @PostMapping
    fun createPolicy(
        @RequestBody request: CreatePricePolicyRequest,
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
        return ResponseEntity.status(201).body(ApiResponse.created(PricePolicyResponse.from(policy)))
    }

    @PutMapping("/{policyId}/options")
    fun updateOptionPrices(
        @PathVariable policyId: Long,
        @RequestBody request: UpdateOptionPricesRequest,
    ): ResponseEntity<ApiResponse<PricePolicyResponse>> {
        val policy = priceCommandUseCase.updateOptionPrices(
            policyId,
            request.optionPrices.map { it.toDomain() },
        )
        return ResponseEntity.ok(ApiResponse.success(PricePolicyResponse.from(policy)))
    }

    @DeleteMapping("/{policyId}")
    fun deletePolicy(
        @PathVariable policyId: Long,
    ): ResponseEntity<Void> {
        priceCommandUseCase.deletePolicy(policyId)
        return ResponseEntity.noContent().build()
    }
}
