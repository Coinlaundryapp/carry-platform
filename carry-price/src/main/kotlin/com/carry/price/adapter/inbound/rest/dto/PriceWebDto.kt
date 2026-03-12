package com.carry.price.adapter.inbound.rest.dto

import com.carry.price.domain.model.PricePolicy
import com.carry.price.domain.vo.OptionPrice
import com.carry.price.domain.vo.OptionType
import com.carry.price.domain.vo.SubOptionType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

@Schema(description = "가격 정책 생성 요청")
data class CreatePricePolicyRequest(
    @Schema(description = "주문 단위 유형", example = "KG")
    @field:NotBlank val orderUnitType: String,
    @Schema(description = "주문 요청 유형", example = "WASH")
    @field:NotBlank val orderRequestType: String,
    @Schema(description = "세탁물 종류", example = "CLOTHING")
    @field:NotBlank val laundryItemType: String,
    @Schema(description = "옵션 가격 목록")
    @field:NotEmpty val optionPrices: List<OptionPriceRequest>,
)

@Schema(description = "옵션 가격 수정 요청")
data class UpdateOptionPricesRequest(
    @Schema(description = "옵션 가격 목록")
    @field:NotEmpty val optionPrices: List<OptionPriceRequest>,
)

@Schema(description = "옵션 가격")
data class OptionPriceRequest(
    @Schema(description = "옵션 유형") val optionType: OptionType,
    @Schema(description = "세부 옵션 유형") val subOptionType: SubOptionType,
    @Schema(description = "가격(원)", example = "5000") val price: Int,
    @Schema(description = "선택 가능 여부") val selectable: Boolean = true,
) {
    fun toDomain() = OptionPrice(optionType, subOptionType, price, selectable)
}

@Schema(description = "총 금액 계산 요청")
data class CalculateTotalRequest(
    @Schema(description = "선택 옵션 목록")
    val selectedOptions: List<SelectedOption>,
)

@Schema(description = "선택 옵션")
data class SelectedOption(
    @Schema(description = "옵션 유형") val optionType: OptionType,
    @Schema(description = "세부 옵션 유형") val subOptionType: SubOptionType,
)

@Schema(description = "옵션 가격 정보")
data class OptionPriceResponse(
    @Schema(description = "옵션 유형") val optionType: OptionType,
    @Schema(description = "세부 옵션 유형") val subOptionType: SubOptionType,
    @Schema(description = "가격(원)") val price: Int,
    @Schema(description = "선택 가능 여부") val selectable: Boolean,
)

@Schema(description = "가격 정책 응답")
data class PricePolicyResponse(
    @Schema(description = "정책 ID") val id: Long,
    @Schema(description = "주문 단위 유형") val orderUnitType: String,
    @Schema(description = "주문 요청 유형") val orderRequestType: String,
    @Schema(description = "세탁물 종류") val laundryItemType: String,
    @Schema(description = "옵션 가격 목록") val optionPrices: List<OptionPriceResponse>,
) {
    companion object {
        fun from(policy: PricePolicy) = PricePolicyResponse(
            id = policy.id!!,
            orderUnitType = policy.condition.orderUnitType.name,
            orderRequestType = policy.condition.orderRequestType.name,
            laundryItemType = policy.condition.laundryItemType.name,
            optionPrices = policy.optionPrices.map {
                OptionPriceResponse(it.optionType, it.subOptionType, it.price, it.selectable)
            },
        )
    }
}

@Schema(description = "총 금액 응답")
data class CalculateTotalResponse(
    @Schema(description = "총 금액(원)")
    val totalAmount: Int,
)
