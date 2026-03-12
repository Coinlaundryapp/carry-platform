package com.carry.delivery.adapter.inbound.rest.dto

import com.carry.delivery.domain.model.Delivery
import com.carry.delivery.domain.model.DeliveryStep
import com.carry.event.delivery.SelectedOptionSnapshot
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.math.BigDecimal
import java.time.Instant

@Schema(description = "수거 완료 요청")
data class CompletePickupRequest(
    @Schema(description = "세탁물 무게(kg)", example = "3.5")
    val weight: BigDecimal,
    @Schema(description = "사진 ID 목록")
    @field:NotEmpty val photoIds: List<Long>,
    @Schema(description = "고객 ID")
    val customerId: Long,
    @Schema(description = "세탁물 종류")
    @field:NotBlank val laundryItemType: String,
    @Schema(description = "주문 단위 유형")
    @field:NotBlank val orderUnitType: String,
    @Schema(description = "주문 요청 유형")
    @field:NotBlank val orderRequestType: String,
    @Schema(description = "선택 옵션 목록")
    val selectedOptions: List<SelectedOptionSnapshot>,
)

@Schema(description = "단계별 사진 요청")
data class StepPhotoRequest(
    @Schema(description = "사진 ID 목록")
    @field:NotEmpty val photoIds: List<Long>,
)

@Schema(description = "배달 응답")
data class DeliveryResponse(
    @Schema(description = "배달 ID") val id: Long,
    @Schema(description = "주문 ID") val orderId: Long,
    @Schema(description = "배차 ID") val dispatchId: Long,
    @Schema(description = "배달원 ID") val carrierId: Long,
    @Schema(description = "세탁소 ID") val laundromatId: Long,
    @Schema(description = "배달 상태") val status: String,
    @Schema(description = "실제 무게(kg)", nullable = true) val actualWeight: BigDecimal?,
    @Schema(description = "배달 단계 목록") val steps: List<DeliveryStepResponse>,
    @Schema(description = "생성 시간") val createdAt: Instant,
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

@Schema(description = "배달 단계 정보")
data class DeliveryStepResponse(
    @Schema(description = "단계 ID", nullable = true) val id: Long?,
    @Schema(description = "단계 유형") val stepType: String,
    @Schema(description = "단계 상태") val status: String,
    @Schema(description = "미디어 ID 목록") val mediaIds: List<Long>,
    @Schema(description = "비고", nullable = true) val note: String?,
    @Schema(description = "완료 시간", nullable = true) val completedAt: Instant?,
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
