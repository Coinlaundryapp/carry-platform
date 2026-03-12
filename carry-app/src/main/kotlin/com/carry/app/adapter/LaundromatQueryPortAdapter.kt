package com.carry.app.adapter

import com.carry.laundromat.application.port.inbound.LaundromatQueryUseCase
import com.carry.laundromat.domain.exception.LaundromatNotFoundException
import com.carry.order.application.port.outbound.LaundromatQueryPort
import org.springframework.stereotype.Component

/**
 * Cross-module adapter: carry-order -> carry-laundromat
 *
 * Implements the LaundromatQueryPort defined in carry-order by delegating to carry-laundromat's LaundromatQueryUseCase.
 */
@Component
class LaundromatQueryPortAdapter(
    private val laundromatQueryUseCase: LaundromatQueryUseCase,
) : LaundromatQueryPort {

    override fun existsById(laundromatId: Long): Boolean {
        return try {
            laundromatQueryUseCase.getById(laundromatId)
            true
        } catch (_: LaundromatNotFoundException) {
            false
        }
    }
}
