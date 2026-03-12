package com.carry.dispatch.application.port.inbound

import com.carry.dispatch.domain.model.CarrierArea

data class RegisterAreaCommand(
    val carrierId: Long,
    val areaCode: String,
    val areaName: String,
)

data class RemoveAreaCommand(
    val carrierId: Long,
    val areaCode: String,
)

interface CarrierAreaUseCase {
    fun registerArea(command: RegisterAreaCommand): CarrierArea
    fun removeArea(command: RemoveAreaCommand)
    fun getCarriersByArea(areaCode: String): List<CarrierArea>
    fun getAreasByCarrier(carrierId: Long): List<CarrierArea>
}
