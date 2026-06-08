package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.LaundromatQueryPort

class FakeLaundromatQueryPort : LaundromatQueryPort {
    private val existingIds = mutableSetOf<Long>()

    fun add(laundromatId: Long) {
        existingIds += laundromatId
    }

    fun clear() {
        existingIds.clear()
    }

    override fun existsById(laundromatId: Long): Boolean = laundromatId in existingIds
}
