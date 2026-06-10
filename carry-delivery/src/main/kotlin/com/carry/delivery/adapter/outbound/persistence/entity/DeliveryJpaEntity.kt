package com.carry.delivery.adapter.outbound.persistence.entity

import com.carry.delivery.domain.model.Delivery
import com.carry.delivery.domain.vo.DeliveryStatus
import com.carry.infra.persistence.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal

@Entity
@Table(name = "delivery_deliveries")
class DeliveryJpaEntity(
    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val dispatchId: Long,

    @Column(nullable = false)
    val carrierId: Long,

    @Column(nullable = false)
    val laundromatId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: DeliveryStatus,

    @Column(precision = 10, scale = 2)
    var actualWeight: BigDecimal?,

    @OneToMany(mappedBy = "delivery", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val steps: MutableList<DeliveryStepJpaEntity> = mutableListOf(),
) : BaseEntity() {

    /**
     * JPA optimistic locking 카운터. 두 트랜잭션이 동일 애그리거트를 동시 변경하면
     * 두 번째 commit에서 OptimisticLockingFailureException이 발생한다.
     */
    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set

    fun toDomain(): Delivery = Delivery.reconstitute(
        id = id,
        orderId = orderId,
        dispatchId = dispatchId,
        carrierId = carrierId,
        laundromatId = laundromatId,
        status = status,
        actualWeight = actualWeight,
        steps = steps.map { it.toDomain() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun updateFrom(delivery: Delivery) {
        status = delivery.status
        actualWeight = delivery.actualWeight

        // steps는 stepType 기준 in-place 갱신(행 식별자·@Version 보존)으로 동기화한다.
        // 자식을 삭제/재삽입하는 "전체 교체"(replaceAllFrom)와 의미가 다르므로 의도적으로
        // 다른 패턴을 유지한다.
        delivery.steps.forEach { domainStep ->
            val existingStep = steps.find { it.stepType == domainStep.stepType }
            existingStep?.updateFrom(domainStep)
        }
    }

    companion object {
        fun fromDomain(delivery: Delivery): DeliveryJpaEntity {
            val entity = DeliveryJpaEntity(
                orderId = delivery.orderId,
                dispatchId = delivery.dispatchId,
                carrierId = delivery.carrierId,
                laundromatId = delivery.laundromatId,
                status = delivery.status,
                actualWeight = delivery.actualWeight,
            )
            delivery.steps.forEach { step ->
                entity.steps.add(DeliveryStepJpaEntity.fromDomain(step, entity))
            }
            return entity
        }
    }
}
