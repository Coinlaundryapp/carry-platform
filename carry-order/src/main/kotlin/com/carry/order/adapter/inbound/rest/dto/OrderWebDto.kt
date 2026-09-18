package com.carry.order.adapter.inbound.rest.dto

import com.carry.order.domain.model.Order
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.math.BigDecimal
import java.time.Instant

@Schema(description = "주문 생성 요청")
data class CreateOrderRequest(
    @Schema(description = "배송지 ID", example = "1")
    val shippingAddressId: Long,
    @Schema(description = "세탁소 ID", example = "1")
    val laundromatId: Long,
    @Schema(description = "세탁물 종류", example = "CLOTHING")
    @field:NotBlank val laundryItemType: String,
    @Schema(description = "선택 옵션 목록")
    @field:NotEmpty @field:Valid val selectedOptions: List<SelectedOptionRequest>,
    @Schema(description = "희망 수거 시간")
    val desiredPickupAt: Instant,
    @Schema(description = "희망 배달 시간")
    val desiredDeliveryAt: Instant,
)

@Schema(description = "선택 옵션")
data class SelectedOptionRequest(
    @Schema(description = "옵션 유형", example = "WASH_TYPE")
    @field:NotBlank val optionType: String,
    @Schema(description = "세부 옵션 유형", example = "DRY_CLEANING")
    @field:NotBlank val subOptionType: String,
)

@Schema(description = "주문 취소 요청")
data class CancelOrderRequest(
    @Schema(description = "취소 사유", example = "고객 변심")
    @field:NotBlank val reason: String,
)

@Schema(description = "주문 응답")
data class OrderResponse(
    @Schema(description = "주문 ID") val id: Long,
    @Schema(description = "고객 ID") val customerId: Long,
    @Schema(description = "주문 상태") val status: String,
    @Schema(description = "세탁소 ID") val laundromatId: Long,
    @Schema(description = "세탁물 종류") val laundryItemType: String,
    @Schema(description = "선택 옵션 목록") val selectedOptions: List<SelectedOptionResponse>,
    @Schema(description = "도로명 주소") val roadAddress: String,
    @Schema(description = "상세 주소") val detailAddress: String,
    @Schema(description = "수령인 이름") val recipientName: String,
    @Schema(description = "수령인 전화번호") val recipientPhone: String,
    @Schema(description = "희망 수거 시간") val desiredPickupAt: Instant,
    @Schema(description = "희망 배달 시간") val desiredDeliveryAt: Instant,
    @Schema(description = "배달원 ID", nullable = true) val carrierId: Long?,
    @Schema(description = "실제 무게(kg)", nullable = true) val actualWeight: BigDecimal?,
    @Schema(description = "취소 사유", nullable = true) val cancelReason: String?,
    @Schema(description = "완료 시간", nullable = true) val completedAt: Instant?,
    @Schema(description = "생성 시간") val createdAt: Instant,
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
            actualWeight = order.actualWeight,
            cancelReason = order.cancellation?.reason,
            completedAt = order.completedAt,
            createdAt = order.createdAt,
        )
    }
}

@Schema(description = "선택 옵션 정보")
data class SelectedOptionResponse(
    @Schema(description = "옵션 유형") val optionType: String,
    @Schema(description = "세부 옵션 유형") val subOptionType: String,
)
