package com.carry.price.adapter.inbound.rest.dto

import com.carry.price.domain.model.PricePolicy
import com.carry.price.domain.vo.OptionPrice
import com.carry.price.domain.vo.OptionType
import com.carry.price.domain.vo.SubOptionType

data class CreatePricePolicyRequest(
    val orderUnitType: String,
    val orderRequestType: String,
    val laundryItemType: String,
    val optionPrices: List<OptionPriceRequest>,
)

data class UpdateOptionPricesRequest(
    val optionPrices: List<OptionPriceRequest>,
)

data class OptionPriceRequest(
    val optionType: OptionType,
    val subOptionType: SubOptionType,
    val price: Int,
    val selectable: Boolean = true,
) {
    fun toDomain() = OptionPrice(optionType, subOptionType, price, selectable)
}

data class CalculateTotalRequest(
    val selectedOptions: List<SelectedOption>,
)

data class SelectedOption(
    val optionType: OptionType,
    val subOptionType: SubOptionType,
)

data class OptionPriceResponse(
    val optionType: OptionType,
    val subOptionType: SubOptionType,
    val price: Int,
    val selectable: Boolean,
)

data class PricePolicyResponse(
    val id: Long,
    val orderUnitType: String,
    val orderRequestType: String,
    val laundryItemType: String,
    val optionPrices: List<OptionPriceResponse>,
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

data class CalculateTotalResponse(
    val totalAmount: Int,
)
