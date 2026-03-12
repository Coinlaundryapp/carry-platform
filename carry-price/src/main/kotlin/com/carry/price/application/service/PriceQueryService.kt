package com.carry.price.application.service

import com.carry.price.application.port.inbound.PriceQueryUseCase
import com.carry.price.application.port.outbound.PricePersistencePort
import com.carry.price.domain.exception.PricePolicyNotFoundException
import com.carry.price.domain.exception.PricePolicyNotFoundForConditionException
import com.carry.price.domain.model.PricePolicy
import com.carry.price.domain.vo.OptionType
import com.carry.price.domain.vo.PriceCondition
import com.carry.price.domain.vo.SubOptionType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PriceQueryService(
    private val pricePersistencePort: PricePersistencePort,
) : PriceQueryUseCase {

    override fun getPolicyByCondition(condition: PriceCondition): PricePolicy {
        return pricePersistencePort.findByCondition(condition)
            ?: throw PricePolicyNotFoundForConditionException(condition)
    }

    override fun getPolicyById(policyId: Long): PricePolicy {
        return pricePersistencePort.findById(policyId)
            ?: throw PricePolicyNotFoundException(policyId)
    }

    override fun calculateTotal(
        condition: PriceCondition,
        selectedOptions: List<Pair<OptionType, SubOptionType>>,
    ): Int {
        val policy = getPolicyByCondition(condition)
        return policy.calculateTotal(selectedOptions)
    }
}
