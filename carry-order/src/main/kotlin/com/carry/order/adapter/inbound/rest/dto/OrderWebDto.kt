package com.carry.order.adapter.inbound.rest.dto

import com.carry.order.domain.model.Order
import java.math.BigDecimal
import java.time.Instant

data class CreateOrderRequest(
    val shippingAddressId: Long,
    val laundromatId: Long,
    val laundryItemType: String,
    val selectedOptions: List<SelectedOptionRequest>,
    val desiredPickupAt: Instant,
    val desiredDeliveryAt: Instant,
)

data class SelectedOptionRequest(
    val optionType: String,
    val subOptionType: String,
)

data class CancelOrderRequest(
    val reason: String,
)

data class OrderResponse(
    val id: Long,
    val customerId: Long,
    val status: String,
    val laundromatId: Long,
    val laundryItemType: String,
    val selectedOptions: List<SelectedOptionResponse>,
    val roadAddress: String,
    val detailAddress: String,
    val recipientName: String,
    val recipientPhone: String,
    val desiredPickupAt: Instant,
    val desiredDeliveryAt: Instant,
    val carrierId: Long?,
    val totalAmount: Long?,
    val actualWeight: BigDecimal?,
    val cancelReason: String?,
    val completedAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun from(order: Order) = OrderResponse(
            id = order.id!!,
            customerId = order.customerId,
            status = order.status.name,
            laundromatId = order.laundromatId,
            laundryItemType = order.laundryItemType,
            selectedOptions = order.selectedOptions.map { SelectedOptionResponse(it.optionType, it.subOptionType) },
            roadAddress = order.shippingAddress.roadAddress,
            detailAddress = order.shippingAddress.detailAddress,
            recipientName = order.shippingAddress.recipientName,
            recipientPhone = order.shippingAddress.recipientPhone,
            desiredPickupAt = order.desiredPickupAt,
            desiredDeliveryAt = order.desiredDeliveryAt,
            carrierId = order.carrierId,
            totalAmount = order.totalAmount,
            actualWeight = order.actualWeight,
            cancelReason = order.cancelReason,
            completedAt = order.completedAt,
            createdAt = order.createdAt,
        )
    }
}

data class SelectedOptionResponse(
    val optionType: String,
    val subOptionType: String,
)
