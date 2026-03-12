package com.carry.price.application.service

import com.carry.price.application.port.inbound.PriceCommandUseCase
import com.carry.price.application.port.outbound.PricePersistencePort
import com.carry.price.domain.exception.DuplicatePricePolicyException
import com.carry.price.domain.exception.PricePolicyNotFoundException
import com.carry.price.domain.model.PricePolicy
import com.carry.price.domain.vo.OptionPrice
import com.carry.price.domain.vo.PriceCondition
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PriceCommandService(
    private val pricePersistencePort: PricePersistencePort,
) : PriceCommandUseCase {

    override fun createPolicy(
        condition: PriceCondition,
        optionPrices: List<OptionPrice>,
    ): PricePolicy {
        if (pricePersistencePort.existsByCondition(condition)) {
            throw DuplicatePricePolicyException(condition)
        }

        val policy = PricePolicy.create(condition, optionPrices)
        return pricePersistencePort.save(policy)
    }

    override fun updateOptionPrices(
        policyId: Long,
        optionPrices: List<OptionPrice>,
    ): PricePolicy {
        val policy = pricePersistencePort.findById(policyId)
            ?: throw PricePolicyNotFoundException(policyId)

        policy.replaceAllOptionPrices(optionPrices)
        return pricePersistencePort.save(policy)
    }

    override fun deletePolicy(policyId: Long) {
        if (pricePersistencePort.findById(policyId) == null) {
            throw PricePolicyNotFoundException(policyId)
        }
        pricePersistencePort.delete(policyId)
    }
}
