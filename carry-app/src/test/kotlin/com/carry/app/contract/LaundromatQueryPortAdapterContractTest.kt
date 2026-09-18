package com.carry.app.contract

import com.carry.app.adapter.LaundromatQueryPortAdapter
import com.carry.app.contract.fake.FakeLaundromatPersistencePort
import com.carry.laundromat.application.service.LaundromatQueryService
import com.carry.order.application.port.outbound.LaundromatQueryPort
import com.carry.order.application.port.outbound.contract.LaundromatQueryPortContract

class LaundromatQueryPortAdapterContractTest : LaundromatQueryPortContract() {

    private val persistence = FakeLaundromatPersistencePort()
    private val adapter = LaundromatQueryPortAdapter(LaundromatQueryService(persistence))

    override fun subject(): LaundromatQueryPort = adapter

    override fun arrangeExisting(laundromatId: Long) {
        persistence.put(laundromatId)
    }

    override fun arrangeMissing(laundromatId: Long) {
        // put 하지 않음 → findById null → 서비스가 LaundromatNotFoundException → 어댑터가 false
    }
}
