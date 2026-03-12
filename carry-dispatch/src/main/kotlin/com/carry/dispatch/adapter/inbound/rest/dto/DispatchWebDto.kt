package com.carry.dispatch.adapter.inbound.rest.dto

import com.carry.dispatch.domain.model.CarrierArea
import com.carry.dispatch.domain.model.Dispatch
import java.time.Instant

data class ClaimDispatchRequest(
    val carrierId: Long,
)

data class AssignDispatchRequest(
    val carrierId: Long,
)

data class AcceptAssignmentRequest(
    val carrierId: Long,
)

data class RejectAssignmentRequest(
    val carrierId: Long,
)

data class CancelDispatchRequest(
    val reason: String,
)

data class RegisterAreaRequest(
    val areaCode: String,
    val areaName: String,
)

data class DispatchResponse(
    val id: Long,
    val orderId: Long,
    val laundromatId: Long,
    val status: String,
    val carrierId: Long?,
    val areaCode: String,
    val desiredPickupAt: Instant,
    val assignedBy: String?,
    val assignedAt: Instant?,
    val acceptedAt: Instant?,
    val cancelReason: String?,
    val createdAt: Instant,
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

data class CarrierAreaResponse(
    val id: Long,
    val carrierId: Long,
    val areaCode: String,
    val areaName: String,
    val active: Boolean,
    val createdAt: Instant,
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
