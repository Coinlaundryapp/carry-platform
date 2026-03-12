package com.carry.laundromat.application.port.outbound

import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.model.NearbyLaundromat

interface LaundromatPersistencePort {

    fun save(laundromat: Laundromat): Laundromat

    fun findById(id: Long): Laundromat?

    fun findNearby(latitude: Double, longitude: Double, radiusMeters: Int): List<NearbyLaundromat>

    fun delete(id: Long)
}
