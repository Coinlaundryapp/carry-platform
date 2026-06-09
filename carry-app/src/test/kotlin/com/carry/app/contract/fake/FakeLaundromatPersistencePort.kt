package com.carry.app.contract.fake

import com.carry.laundromat.application.port.outbound.LaundromatPersistencePort
import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.model.NearbyLaundromat
import com.carry.laundromat.domain.vo.LaundromatAddress
import com.carry.laundromat.domain.vo.Location
import java.time.Instant

class FakeLaundromatPersistencePort : LaundromatPersistencePort {
    private val store = mutableMapOf<Long, Laundromat>()

    fun put(id: Long) {
        store[id] = Laundromat.reconstitute(
            id = id,
            name = "테스트세탁소",
            address = LaundromatAddress("서울시 강남구 테헤란로 1"),
            location = Location(37.5, 127.0),
            options = emptySet(),
            mediaResources = emptyList(),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    override fun save(laundromat: Laundromat): Laundromat = laundromat
    override fun findById(id: Long): Laundromat? = store[id]
    override fun findNearby(latitude: Double, longitude: Double, radiusMeters: Int): List<NearbyLaundromat> = emptyList()
    override fun delete(id: Long) { store.remove(id) }
}
