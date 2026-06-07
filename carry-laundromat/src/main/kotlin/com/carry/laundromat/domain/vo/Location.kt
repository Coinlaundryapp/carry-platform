package com.carry.laundromat.domain.vo

import com.carry.common.exception.requireInput

data class Location(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        requireInput(latitude in -90.0..90.0) { "위도는 -90~90 범위여야 합니다: $latitude" }
        requireInput(longitude in -180.0..180.0) { "경도는 -180~180 범위여야 합니다: $longitude" }
    }
}
