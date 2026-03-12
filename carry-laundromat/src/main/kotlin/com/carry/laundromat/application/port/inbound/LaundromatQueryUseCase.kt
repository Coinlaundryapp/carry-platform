package com.carry.laundromat.application.port.inbound

import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.model.NearbyLaundromat

interface LaundromatQueryUseCase {

    fun getById(laundromatId: Long): Laundromat

    fun findNearby(latitude: Double, longitude: Double, radiusMeters: Int): List<NearbyLaundromat>
}
