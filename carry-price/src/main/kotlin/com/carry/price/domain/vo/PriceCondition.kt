package com.carry.price.domain.vo

data class PriceCondition(
    val orderUnitType: OrderUnitType,
    val orderRequestType: OrderRequestType,
    val laundryItemType: LaundryItemType,
)
