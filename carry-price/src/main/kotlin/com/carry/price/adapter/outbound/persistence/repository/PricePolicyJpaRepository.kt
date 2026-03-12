package com.carry.price.adapter.outbound.persistence.repository

import com.carry.price.adapter.outbound.persistence.entity.PricePolicyJpaEntity
import com.carry.price.domain.vo.LaundryItemType
import com.carry.price.domain.vo.OrderRequestType
import com.carry.price.domain.vo.OrderUnitType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PricePolicyJpaRepository : JpaRepository<PricePolicyJpaEntity, Long> {

    fun findByOrderUnitTypeAndOrderRequestTypeAndLaundryItemType(
        orderUnitType: OrderUnitType,
        orderRequestType: OrderRequestType,
        laundryItemType: LaundryItemType,
    ): Optional<PricePolicyJpaEntity>

    fun existsByOrderUnitTypeAndOrderRequestTypeAndLaundryItemType(
        orderUnitType: OrderUnitType,
        orderRequestType: OrderRequestType,
        laundryItemType: LaundryItemType,
    ): Boolean
}
