package com.carry.geo.domain.model

data class ReverseGeocodingResult(
    val country: String,
    val si: String,
    val gu: String,
    val dong: String,
) {
    fun toFullAddress(): String =
        listOf(si, gu, dong).filter { it.isNotBlank() }.joinToString(" ")
}
