package com.carry.price.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.price.domain.vo.OptionPrice
import com.carry.price.domain.vo.OptionType
import com.carry.price.domain.vo.SubOptionType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "price_option_prices")
class OptionPriceJpaEntity(
    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false)
    val optionType: OptionType,

    @Enumerated(EnumType.STRING)
    @Column(name = "sub_option_type", nullable = false)
    val subOptionType: SubOptionType,

    @Column(nullable = false)
    val price: Int,

    @Column(nullable = false)
    val selectable: Boolean = true,
) : BaseEntity() {

    fun toDomain(): OptionPrice = OptionPrice(
        optionType = optionType,
        subOptionType = subOptionType,
        price = price,
        selectable = selectable,
    )

    companion object {
        fun fromDomain(optionPrice: OptionPrice): OptionPriceJpaEntity = OptionPriceJpaEntity(
            optionType = optionPrice.optionType,
            subOptionType = optionPrice.subOptionType,
            price = optionPrice.price,
            selectable = optionPrice.selectable,
        )
    }
}
