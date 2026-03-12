package com.carry.laundromat.adapter.outbound.persistence.repository

import com.carry.laundromat.adapter.outbound.persistence.entity.LaundromatJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface NearbyLaundromatRow {
    fun getId(): Long
    fun getDistance(): Double
}

interface LaundromatJpaRepository : JpaRepository<LaundromatJpaEntity, Long> {

    @Query(
        value = """
            SELECT l.id AS id,
                   ST_Distance(
                       ST_MakePoint(l.longitude, l.latitude)::geography,
                       ST_MakePoint(:longitude, :latitude)::geography
                   ) AS distance
            FROM laundromat_laundromats l
            WHERE ST_DWithin(
                ST_MakePoint(l.longitude, l.latitude)::geography,
                ST_MakePoint(:longitude, :latitude)::geography,
                :radiusMeters
            )
            ORDER BY distance
        """,
        nativeQuery = true,
    )
    fun findNearbyIds(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): List<NearbyLaundromatRow>
}
