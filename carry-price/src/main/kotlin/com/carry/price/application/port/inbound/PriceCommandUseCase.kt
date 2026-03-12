package com.carry.price.application.port.inbound

import com.carry.price.domain.model.PricePolicy
import com.carry.price.domain.vo.OptionPrice
import com.carry.price.domain.vo.PriceCondition

interface PriceCommandUseCase {

    fun createPolicy(condition: PriceCondition, optionPrices: List<OptionPrice>): PricePolicy

    fun updateOptionPrices(policyId: Long, optionPrices: List<OptionPrice>): PricePolicy

    fun deletePolicy(policyId: Long)
}
