package com.carry.price.domain.vo

data class OptionPrice(
    val optionType: OptionType,
    val subOptionType: SubOptionType,
    val price: Int,
    val selectable: Boolean = true,
) {
    init {
        require(price >= 0) { "가격은 0 이상이어야 합니다: $price" }
    }
}
