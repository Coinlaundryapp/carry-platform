package com.carry.laundromat.adapter.inbound.rest.dto

import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.model.NearbyLaundromat
import com.carry.laundromat.domain.vo.LaundromatAddress
import com.carry.laundromat.domain.vo.LaundromatOption
import com.carry.laundromat.domain.vo.Location

data class RegisterLaundromatRequest(
    val name: String,
    val roadAddress: String,
    val detailAddress: String? = null,
    val zipCode: String? = null,
    val latitude: Double,
    val longitude: Double,
    val options: Set<LaundromatOption> = emptySet(),
) {
    fun toAddress() = LaundromatAddress(roadAddress, detailAddress, zipCode)
    fun toLocation() = Location(latitude, longitude)
}

data class UpdateLaundromatInfoRequest(
    val name: String,
    val roadAddress: String,
    val detailAddress: String? = null,
    val zipCode: String? = null,
    val latitude: Double,
    val longitude: Double,
) {
    fun toAddress() = LaundromatAddress(roadAddress, detailAddress, zipCode)
    fun toLocation() = Location(latitude, longitude)
}

data class UpdateOptionsRequest(
    val options: Set<LaundromatOption>,
)

data class AddMediaResourceRequest(
    val url: String,
    val extension: String,
)

data class MediaResourceResponse(
    val id: Long,
    val url: String,
    val extension: String,
)

data class LaundromatResponse(
    val id: Long,
    val name: String,
    val roadAddress: String,
    val detailAddress: String?,
    val zipCode: String?,
    val latitude: Double,
    val longitude: Double,
    val options: Set<LaundromatOption>,
    val mediaResources: List<MediaResourceResponse>,
) {
    companion object {
        fun from(laundromat: Laundromat) = LaundromatResponse(
            id = laundromat.id!!,
            name = laundromat.name,
            roadAddress = laundromat.address.roadAddress,
            detailAddress = laundromat.address.detailAddress,
            zipCode = laundromat.address.zipCode,
            latitude = laundromat.location.latitude,
            longitude = laundromat.location.longitude,
            options = laundromat.options,
            mediaResources = laundromat.mediaResources.map {
                MediaResourceResponse(it.id!!, it.url, it.extension)
            },
        )
    }
}

data class NearbyLaundromatResponse(
    val laundromat: LaundromatResponse,
    val distanceMeters: Double,
) {
    companion object {
        fun from(nearby: NearbyLaundromat) = NearbyLaundromatResponse(
            laundromat = LaundromatResponse.from(nearby.laundromat),
            distanceMeters = nearby.distanceMeters,
        )
    }
}
