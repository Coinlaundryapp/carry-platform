package com.carry.delivery.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "delivery_step_media")
class DeliveryStepMediaJpaEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_step_id", nullable = false)
    val deliveryStep: DeliveryStepJpaEntity,

    @Column(nullable = false)
    val mediaId: Long,
) : BaseEntity()
