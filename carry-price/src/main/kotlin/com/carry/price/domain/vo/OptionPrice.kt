package com.carry.price.domain.vo

import com.carry.common.exception.requireInput

data class OptionPrice(
    val optionType: OptionType,
    val subOptionType: SubOptionType,
    val price: Int,
    val selectable: Boolean = true,
) {
    init {
        requireInput(price >= 0) { "가격은 0 이상이어야 합니다: $price" }
    }
}
