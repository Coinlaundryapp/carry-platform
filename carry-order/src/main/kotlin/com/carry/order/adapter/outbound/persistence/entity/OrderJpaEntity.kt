package com.carry.order.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.order.domain.model.Order
import com.carry.order.domain.vo.CancelledBy
import com.carry.order.domain.vo.OrderCancellation
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.order.domain.vo.OrderStatus
import com.carry.order.domain.vo.SelectedOption
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
import java.time.Instant

@Entity
@Table(name = "orders")
class OrderJpaEntity(
    @Column(nullable = false)
    val customerId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: OrderStatus,

    @Column(nullable = false)
    val laundromatId: Long,

    @Column(nullable = false, length = 30)
    val laundryItemType: String,

    @Column(nullable = false)
    val roadAddress: String,
    @Column(nullable = false)
    val detailAddress: String,
    val zipCode: String?,
    @Column(nullable = false)
    val latitude: Double,
    @Column(nullable = false)
    val longitude: Double,
    @Column(nullable = false, length = 50)
    val recipientName: String,
    @Column(nullable = false, length = 20)
    val recipientPhone: String,
    val entranceInfo: String?,
    @Column(nullable = false, length = 20)
    val areaCode: String,

    @Column(nullable = false)
    val desiredPickupAt: Instant,
    @Column(nullable = false)
    val desiredDeliveryAt: Instant,

    var carrierId: Long?,
    @Column(precision = 10, scale = 2)
    var actualWeight: BigDecimal?,

    var cancelReason: String?,
    @Enumerated(EnumType.STRING)
    var cancelledBy: CancelledBy?,
    var cancelledAt: Instant?,
    var completedAt: Instant?,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val selectedOptions: MutableList<OrderSelectedOptionJpaEntity> = mutableListOf(),
) : BaseEntity() {

    /**
     * JPA optimistic locking 카운터. 두 트랜잭션이 동일 주문을 동시 변경(특히 취소)할 때
     * 두 번째 트랜잭션 commit에서 `OptimisticLockingFailureException`이 발생한다.
     */
    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set


    fun toDomain(): Order = Order.reconstitute(
        id = id!!,
        customerId = customerId,
        status = status,
        laundromatId = laundromatId,
        laundryItemType = laundryItemType,
        selectedOptions = selectedOptions.map { SelectedOption(it.optionType, it.subOptionType) },
        shippingAddress = OrderShippingAddress(
            roadAddress, detailAddress, zipCode, latitude, longitude,
            recipientName, recipientPhone, entranceInfo, areaCode,
        ),
        desiredPickupAt = desiredPickupAt,
        desiredDeliveryAt = desiredDeliveryAt,
        carrierId = carrierId,
        actualWeight = actualWeight,
        // 취소 컬럼은 cancel() 에서 항상 함께 채워지므로 셋 중 하나(사유)로 존재 여부를 판정한다.
        cancellation = cancelReason?.let { OrderCancellation(it, cancelledBy!!, cancelledAt!!) },
        completedAt = completedAt,
        createdAt = createdAt!!,
        updatedAt = updatedAt!!,
    )

    fun updateFrom(order: Order) {
        status = order.status
        carrierId = order.carrierId
        actualWeight = order.actualWeight
        cancelReason = order.cancellation?.reason
        cancelledBy = order.cancellation?.by
        cancelledAt = order.cancellation?.at
        completedAt = order.completedAt
    }

    companion object {
        fun fromDomain(order: Order): OrderJpaEntity {
            val entity = OrderJpaEntity(
                customerId = order.customerId,
                status = order.status,
                laundromatId = order.laundromatId,
                laundryItemType = order.laundryItemType,
                roadAddress = order.shippingAddress.roadAddress,
                detailAddress = order.shippingAddress.detailAddress,
                zipCode = order.shippingAddress.zipCode,
                latitude = order.shippingAddress.latitude,
                longitude = order.shippingAddress.longitude,
                recipientName = order.shippingAddress.recipientName,
                recipientPhone = order.shippingAddress.recipientPhone,
                entranceInfo = order.shippingAddress.entranceInfo,
                areaCode = order.shippingAddress.areaCode,
                desiredPickupAt = order.desiredPickupAt,
                desiredDeliveryAt = order.desiredDeliveryAt,
                carrierId = order.carrierId,
                actualWeight = order.actualWeight,
                cancelReason = order.cancellation?.reason,
                cancelledBy = order.cancellation?.by,
                cancelledAt = order.cancellation?.at,
                completedAt = order.completedAt,
            )
            order.selectedOptions.forEach { opt ->
                entity.selectedOptions.add(
                    OrderSelectedOptionJpaEntity(
                        order = entity,
                        optionType = opt.optionType,
                        subOptionType = opt.subOptionType,
                    ),
                )
            }
            return entity
        }
    }
}
