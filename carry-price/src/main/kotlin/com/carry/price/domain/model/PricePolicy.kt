package com.carry.price.domain.model

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.price.domain.vo.OptionPrice
import com.carry.price.domain.vo.OptionType
import com.carry.price.domain.vo.PriceCondition
import com.carry.price.domain.vo.SubOptionType
import java.time.Instant

class PricePolicy private constructor(
    val id: Long?,
    val condition: PriceCondition,
    private val _optionPrices: MutableList<OptionPrice>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    val optionPrices: List<OptionPrice> get() = _optionPrices.toList()

    fun findOptionPrice(optionType: OptionType, subOptionType: SubOptionType): OptionPrice? =
        _optionPrices.find { it.optionType == optionType && it.subOptionType == subOptionType }

    fun setOptionPrice(
        optionType: OptionType,
        subOptionType: SubOptionType,
        price: Int,
        selectable: Boolean,
    ) {
        _optionPrices.removeIf { it.optionType == optionType && it.subOptionType == subOptionType }
        _optionPrices.add(OptionPrice(optionType, subOptionType, price, selectable))
    }

    fun replaceAllOptionPrices(prices: List<OptionPrice>) {
        val keys = prices.map { it.optionType to it.subOptionType }
        require(keys.size == keys.distinct().size) { "중복된 옵션 가격이 있습니다" }
        _optionPrices.clear()
        _optionPrices.addAll(prices)
    }

    fun calculateTotal(selectedOptions: List<Pair<OptionType, SubOptionType>>): Int {
        return selectedOptions.sumOf { (optionType, subOptionType) ->
            val optionPrice = findOptionPrice(optionType, subOptionType)
                ?: throw BusinessException(ErrorCode.OPTION_NOT_FOUND, "존재하지 않는 옵션입니다: $optionType/$subOptionType")
            if (!optionPrice.selectable) {
                throw BusinessException(ErrorCode.OPTION_NOT_SELECTABLE, "선택 불가능한 옵션입니다: $optionType/$subOptionType")
            }
            optionPrice.price
        }
    }

    fun getSelectableOptions(): List<OptionPrice> =
        _optionPrices.filter { it.selectable }

    fun getOptionsByType(optionType: OptionType): List<OptionPrice> =
        _optionPrices.filter { it.optionType == optionType }

    companion object {
        fun create(
            condition: PriceCondition,
            optionPrices: List<OptionPrice> = emptyList(),
        ): PricePolicy {
            val keys = optionPrices.map { it.optionType to it.subOptionType }
            require(keys.size == keys.distinct().size) { "중복된 옵션 가격이 있습니다" }
            return PricePolicy(
                id = null,
                condition = condition,
                _optionPrices = optionPrices.toMutableList(),
                createdAt = null,
                updatedAt = null,
            )
        }

        fun reconstitute(
            id: Long,
            condition: PriceCondition,
            optionPrices: List<OptionPrice>,
            createdAt: Instant,
            updatedAt: Instant,
        ): PricePolicy = PricePolicy(
            id = id,
            condition = condition,
            _optionPrices = optionPrices.toMutableList(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
