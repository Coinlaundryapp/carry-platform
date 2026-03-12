package com.carry.geo.adapter.outbound.external.naver.dto

import com.carry.geo.domain.model.ReverseGeocodingResult
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverReverseGeocodingResponse(
    val status: Status,
    val results: List<Result>,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Status(
        val code: Int,
        val name: String,
        val message: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Result(
        val name: String,
        val code: Code?,
        val region: Region,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Code(
        val id: String,
        val type: String,
        val mappingId: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Region(
        val area0: Area,
        val area1: Area,
        val area2: Area,
        val area3: Area,
        val area4: Area?,
    ) {
        fun toDomain(): ReverseGeocodingResult = ReverseGeocodingResult(
            country = area0.name,
            si = area1.name,
            gu = area2.name,
            dong = area3.name,
        )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Area(
        val name: String,
        val coords: Coords?,
        val alias: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Coords(
        val center: Center?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Center(
        val crs: String?,
        val x: Double,
        val y: Double,
    )
}
