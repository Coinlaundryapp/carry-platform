package com.carry.geo.domain.vo

data class Coordinate(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "위도는 -90 ~ 90 범위여야 합니다: $latitude" }
        require(longitude in -180.0..180.0) { "경도는 -180 ~ 180 범위여야 합니다: $longitude" }
    }

    fun toNaverCoordsFormat(): String = "$longitude,$latitude"
}
