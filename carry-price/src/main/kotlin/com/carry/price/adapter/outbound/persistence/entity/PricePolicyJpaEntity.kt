package com.carry.price.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.infra.persistence.replaceAllFrom
import com.carry.price.domain.model.PricePolicy
import com.carry.price.domain.vo.LaundryItemType
import com.carry.price.domain.vo.OrderRequestType
import com.carry.price.domain.vo.OrderUnitType
import com.carry.price.domain.vo.PriceCondition
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "price_policies",
    uniqueConstraints = [
        UniqueConstraint(
            columnNames = ["order_unit_type", "order_request_type", "laundry_item_type"],
        ),
    ],
)
class PricePolicyJpaEntity(
    @Enumerated(EnumType.STRING)
    @Column(name = "order_unit_type", nullable = false)
    val orderUnitType: OrderUnitType,

    @Enumerated(EnumType.STRING)
    @Column(name = "order_request_type", nullable = false)
    val orderRequestType: OrderRequestType,

    @Enumerated(EnumType.STRING)
    @Column(name = "laundry_item_type", nullable = false)
    val laundryItemType: LaundryItemType,

    @OneToMany(
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER,
    )
    @JoinColumn(name = "policy_id")
    val optionPrices: MutableList<OptionPriceJpaEntity> = mutableListOf(),
) : BaseEntity() {

    fun toDomain(): PricePolicy = PricePolicy.reconstitute(
        id = id,
        condition = PriceCondition(orderUnitType, orderRequestType, laundryItemType),
        optionPrices = optionPrices.map { it.toDomain() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun updateFrom(policy: PricePolicy) {
        optionPrices.replaceAllFrom(policy.optionPrices) { OptionPriceJpaEntity.fromDomain(it) }
    }

    companion object {
        fun fromDomain(policy: PricePolicy): PricePolicyJpaEntity = PricePolicyJpaEntity(
            orderUnitType = policy.condition.orderUnitType,
            orderRequestType = policy.condition.orderRequestType,
            laundryItemType = policy.condition.laundryItemType,
            optionPrices = policy.optionPrices
                .map { OptionPriceJpaEntity.fromDomain(it) }
                .toMutableList(),
        )
    }
}
