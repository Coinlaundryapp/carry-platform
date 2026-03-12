package com.carry.laundromat.application.service

import com.carry.laundromat.application.port.inbound.LaundromatQueryUseCase
import com.carry.laundromat.application.port.outbound.LaundromatPersistencePort
import com.carry.laundromat.domain.exception.LaundromatNotFoundException
import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.model.NearbyLaundromat
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class LaundromatQueryService(
    private val laundromatPersistencePort: LaundromatPersistencePort,
) : LaundromatQueryUseCase {

    override fun getById(laundromatId: Long): Laundromat {
        return laundromatPersistencePort.findById(laundromatId)
            ?: throw LaundromatNotFoundException(laundromatId)
    }

    override fun findNearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): List<NearbyLaundromat> {
        return laundromatPersistencePort.findNearby(latitude, longitude, radiusMeters)
    }
}
