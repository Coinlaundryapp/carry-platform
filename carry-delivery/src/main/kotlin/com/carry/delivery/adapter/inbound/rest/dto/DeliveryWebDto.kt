package com.carry.delivery.adapter.inbound.rest.dto

import com.carry.delivery.domain.model.Delivery
import com.carry.delivery.domain.model.DeliveryStep
import com.carry.event.delivery.SelectedOptionSnapshot
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.math.BigDecimal
import java.time.Instant

data class CompletePickupRequest(
    val weight: BigDecimal,
    @field:NotEmpty val photoIds: List<Long>,
    val customerId: Long,
    @field:NotBlank val laundryItemType: String,
    @field:NotBlank val orderUnitType: String,
    @field:NotBlank val orderRequestType: String,
    val selectedOptions: List<SelectedOptionSnapshot>,
)

data class StepPhotoRequest(
    @field:NotEmpty val photoIds: List<Long>,
)

data class DeliveryResponse(
    val id: Long,
    val orderId: Long,
    val dispatchId: Long,
    val carrierId: Long,
    val laundromatId: Long,
    val status: String,
    val actualWeight: BigDecimal?,
    val steps: List<DeliveryStepResponse>,
    val createdAt: Instant,
) {
    companion object {
        fun from(delivery: Delivery) = DeliveryResponse(
            id = delivery.id!!,
            orderId = delivery.orderId,
            dispatchId = delivery.dispatchId,
            carrierId = delivery.carrierId,
            laundromatId = delivery.laundromatId,
            status = delivery.status.name,
            actualWeight = delivery.actualWeight,
            steps = delivery.steps.map { DeliveryStepResponse.from(it) },
            createdAt = delivery.createdAt,
        )
    }
}

data class DeliveryStepResponse(
    val id: Long?,
    val stepType: String,
    val status: String,
    val mediaIds: List<Long>,
    val note: String?,
    val completedAt: Instant?,
) {
    companion object {
        fun from(step: DeliveryStep) = DeliveryStepResponse(
            id = step.id,
            stepType = step.stepType.name,
            status = step.status.name,
            mediaIds = step.mediaIds,
            note = step.note,
            completedAt = step.completedAt,
        )
    }
}
