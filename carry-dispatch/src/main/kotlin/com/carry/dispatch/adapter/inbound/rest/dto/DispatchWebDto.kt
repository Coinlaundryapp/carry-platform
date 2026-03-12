package com.carry.dispatch.adapter.inbound.rest.dto

import com.carry.dispatch.domain.model.CarrierArea
import com.carry.dispatch.domain.model.Dispatch
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Schema(description = "배차 선점 요청")
data class ClaimDispatchRequest(
    @Schema(description = "배달원 ID")
    val carrierId: Long,
)

@Schema(description = "배차 배정 요청")
data class AssignDispatchRequest(
    @Schema(description = "배달원 ID")
    val carrierId: Long,
)

@Schema(description = "배차 수락 요청")
data class AcceptAssignmentRequest(
    @Schema(description = "배달원 ID")
    val carrierId: Long,
)

@Schema(description = "배차 거절 요청")
data class RejectAssignmentRequest(
    @Schema(description = "배달원 ID")
    val carrierId: Long,
)

@Schema(description = "배차 취소 요청")
data class CancelDispatchRequest(
    @Schema(description = "취소 사유")
    @field:NotBlank val reason: String,
)

@Schema(description = "권역 등록 요청")
data class RegisterAreaRequest(
    @Schema(description = "권역 코드", example = "GANGNAM")
    @field:NotBlank val areaCode: String,
    @Schema(description = "권역명", example = "강남구")
    @field:NotBlank val areaName: String,
)

@Schema(description = "배차 응답")
data class DispatchResponse(
    @Schema(description = "배차 ID") val id: Long,
    @Schema(description = "주문 ID") val orderId: Long,
    @Schema(description = "세탁소 ID") val laundromatId: Long,
    @Schema(description = "배차 상태") val status: String,
    @Schema(description = "배달원 ID", nullable = true) val carrierId: Long?,
    @Schema(description = "권역 코드") val areaCode: String,
    @Schema(description = "희망 수거 시간") val desiredPickupAt: Instant,
    @Schema(description = "배정 방식", nullable = true) val assignedBy: String?,
    @Schema(description = "배정 시간", nullable = true) val assignedAt: Instant?,
    @Schema(description = "수락 시간", nullable = true) val acceptedAt: Instant?,
    @Schema(description = "취소 사유", nullable = true) val cancelReason: String?,
    @Schema(description = "생성 시간") val createdAt: Instant,
) {
    companion object {
        fun from(dispatch: Dispatch) = DispatchResponse(
            id = dispatch.id!!,
            orderId = dispatch.orderId,
            laundromatId = dispatch.laundromatId,
            status = dispatch.status.name,
            carrierId = dispatch.carrierId,
            areaCode = dispatch.areaCode,
            desiredPickupAt = dispatch.desiredPickupAt,
            assignedBy = dispatch.assignedBy?.name,
            assignedAt = dispatch.assignedAt,
            acceptedAt = dispatch.acceptedAt,
            cancelReason = dispatch.cancelReason,
            createdAt = dispatch.createdAt,
        )
    }
}

@Schema(description = "배달원 권역 응답")
data class CarrierAreaResponse(
    @Schema(description = "권역 매핑 ID") val id: Long,
    @Schema(description = "배달원 ID") val carrierId: Long,
    @Schema(description = "권역 코드") val areaCode: String,
    @Schema(description = "권역명") val areaName: String,
    @Schema(description = "활성 여부") val active: Boolean,
    @Schema(description = "생성 시간") val createdAt: Instant,
) {
    companion object {
        fun from(carrierArea: CarrierArea) = CarrierAreaResponse(
            id = carrierArea.id!!,
            carrierId = carrierArea.carrierId,
            areaCode = carrierArea.areaCode,
            areaName = carrierArea.areaName,
            active = carrierArea.active,
            createdAt = carrierArea.createdAt,
        )
    }
}
