package com.carry.laundromat.application.service

import com.carry.laundromat.application.port.inbound.LaundromatCommandUseCase
import com.carry.laundromat.application.port.outbound.LaundromatPersistencePort
import com.carry.laundromat.domain.exception.LaundromatNotFoundException
import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.vo.LaundromatAddress
import com.carry.laundromat.domain.vo.LaundromatOption
import com.carry.laundromat.domain.vo.Location
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class LaundromatCommandService(
    private val laundromatPersistencePort: LaundromatPersistencePort,
) : LaundromatCommandUseCase {

    override fun register(
        name: String,
        address: LaundromatAddress,
        location: Location,
        options: Set<LaundromatOption>,
    ): Laundromat {
        val laundromat = Laundromat.create(
            name = name,
            address = address,
            location = location,
            options = options,
        )
        return laundromatPersistencePort.save(laundromat)
    }

    override fun updateInfo(
        laundromatId: Long,
        name: String,
        address: LaundromatAddress,
        location: Location,
    ): Laundromat {
        val laundromat = findOrThrow(laundromatId)
        laundromat.updateInfo(name, address, location)
        return laundromatPersistencePort.save(laundromat)
    }

    override fun updateOptions(
        laundromatId: Long,
        options: Set<LaundromatOption>,
    ): Laundromat {
        val laundromat = findOrThrow(laundromatId)
        laundromat.replaceOptions(options)
        return laundromatPersistencePort.save(laundromat)
    }

    override fun addMediaResource(
        laundromatId: Long,
        url: String,
        extension: String,
    ): Laundromat {
        val laundromat = findOrThrow(laundromatId)
        laundromat.addMediaResource(url, extension)
        return laundromatPersistencePort.save(laundromat)
    }

    override fun removeMediaResource(
        laundromatId: Long,
        mediaResourceId: Long,
    ): Laundromat {
        val laundromat = findOrThrow(laundromatId)
        laundromat.removeMediaResource(mediaResourceId)
        return laundromatPersistencePort.save(laundromat)
    }

    private fun findOrThrow(laundromatId: Long): Laundromat {
        return laundromatPersistencePort.findById(laundromatId)
            ?: throw LaundromatNotFoundException(laundromatId)
    }
}
