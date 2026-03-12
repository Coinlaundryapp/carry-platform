package com.carry.order.application.port.outbound

interface LaundromatQueryPort {
    fun existsById(laundromatId: Long): Boolean
}
