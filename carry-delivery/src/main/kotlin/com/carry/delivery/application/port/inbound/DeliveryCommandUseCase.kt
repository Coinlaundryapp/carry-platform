package com.carry.delivery.application.port.inbound

import com.carry.delivery.domain.model.Delivery
import com.carry.event.delivery.SelectedOptionSnapshot
import java.math.BigDecimal

interface DeliveryCommandUseCase {
    fun completePickup(
        deliveryId: Long,
        weight: BigDecimal,
        photoIds: List<Long>,
        customerId: Long,
        laundryItemType: String,
        orderUnitType: String,
        orderRequestType: String,
        selectedOptions: List<SelectedOptionSnapshot>,
        requestingCarrierId: Long,
    ): Delivery

    fun startWashing(deliveryId: Long, photoIds: List<Long>, requestingCarrierId: Long): Delivery
    fun completeDrying(deliveryId: Long, photoIds: List<Long>, requestingCarrierId: Long): Delivery
    fun startDelivery(deliveryId: Long, requestingCarrierId: Long): Delivery
    fun completeDelivery(deliveryId: Long, photoIds: List<Long>, requestingCarrierId: Long): Delivery
}
