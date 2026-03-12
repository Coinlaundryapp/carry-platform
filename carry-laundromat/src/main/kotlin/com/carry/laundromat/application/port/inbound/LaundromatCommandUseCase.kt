package com.carry.laundromat.application.port.inbound

import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.vo.LaundromatAddress
import com.carry.laundromat.domain.vo.LaundromatOption
import com.carry.laundromat.domain.vo.Location

interface LaundromatCommandUseCase {

    fun register(
        name: String,
        address: LaundromatAddress,
        location: Location,
        options: Set<LaundromatOption>,
    ): Laundromat

    fun updateInfo(
        laundromatId: Long,
        name: String,
        address: LaundromatAddress,
        location: Location,
    ): Laundromat

    fun updateOptions(laundromatId: Long, options: Set<LaundromatOption>): Laundromat

    fun addMediaResource(laundromatId: Long, url: String, extension: String): Laundromat

    fun removeMediaResource(laundromatId: Long, mediaResourceId: Long): Laundromat
}
