package com.carry.order.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "order_selected_options")
class OrderSelectedOptionJpaEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val order: OrderJpaEntity,

    @Column(nullable = false, length = 30)
    val optionType: String,

    @Column(nullable = false, length = 30)
    val subOptionType: String,
) : BaseEntity()
