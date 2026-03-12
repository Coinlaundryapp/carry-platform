package com.carry.price.application.port.inbound

import com.carry.price.domain.model.PricePolicy
import com.carry.price.domain.vo.OptionType
import com.carry.price.domain.vo.PriceCondition
import com.carry.price.domain.vo.SubOptionType

interface PriceQueryUseCase {

    fun getPolicyByCondition(condition: PriceCondition): PricePolicy

    fun getPolicyById(policyId: Long): PricePolicy

    fun calculateTotal(
        condition: PriceCondition,
        selectedOptions: List<Pair<OptionType, SubOptionType>>,
    ): Int
}
