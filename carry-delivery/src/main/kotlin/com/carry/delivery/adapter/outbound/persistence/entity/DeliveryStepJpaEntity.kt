package com.carry.delivery.adapter.outbound.persistence.entity

import com.carry.delivery.domain.model.DeliveryStep
import com.carry.delivery.domain.vo.DeliveryStepType
import com.carry.delivery.domain.vo.StepStatus
import com.carry.infra.persistence.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "delivery_steps")
class DeliveryStepJpaEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    val delivery: DeliveryJpaEntity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val stepType: DeliveryStepType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: StepStatus,

    var note: String?,

    var completedAt: Instant?,

    @OneToMany(mappedBy = "deliveryStep", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val media: MutableList<DeliveryStepMediaJpaEntity> = mutableListOf(),
) : BaseEntity() {

    fun toDomain(): DeliveryStep = DeliveryStep.reconstitute(
        id = id,
        deliveryId = delivery.id,
        stepType = stepType,
        status = status,
        mediaIds = media.map { it.mediaId },
        note = note,
        completedAt = completedAt,
    )

    fun updateFrom(step: DeliveryStep) {
        status = step.status
        note = step.note
        completedAt = step.completedAt

        // Sync media
        media.clear()
        step.mediaIds.forEach { mediaId ->
            media.add(DeliveryStepMediaJpaEntity(deliveryStep = this, mediaId = mediaId))
        }
    }

    companion object {
        fun fromDomain(step: DeliveryStep, deliveryEntity: DeliveryJpaEntity): DeliveryStepJpaEntity {
            val entity = DeliveryStepJpaEntity(
                delivery = deliveryEntity,
                stepType = step.stepType,
                status = step.status,
                note = step.note,
                completedAt = step.completedAt,
            )
            step.mediaIds.forEach { mediaId ->
                entity.media.add(DeliveryStepMediaJpaEntity(deliveryStep = entity, mediaId = mediaId))
            }
            return entity
        }
    }
}
