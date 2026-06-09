package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.LaundromatQueryPort

class FakeLaundromatQueryPortContractTest : LaundromatQueryPortContract() {

    private val fake = FakeLaundromatQueryPort()

    override fun subject(): LaundromatQueryPort = fake

    override fun arrangeExisting(laundromatId: Long) {
        fake.add(laundromatId)
    }

    override fun arrangeMissing(laundromatId: Long) {
        // 추가하지 않음 = 부재
    }
}
