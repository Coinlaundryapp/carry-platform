package com.carry.price.application.port.outbound

import com.carry.price.domain.model.PricePolicy
import com.carry.price.domain.vo.PriceCondition

interface PricePersistencePort {

    fun save(policy: PricePolicy): PricePolicy

    fun findById(id: Long): PricePolicy?

    fun findByCondition(condition: PriceCondition): PricePolicy?

    fun existsByCondition(condition: PriceCondition): Boolean

    fun delete(id: Long)
}
