package com.carry.geo.adapter.outbound.external.naver.dto

import com.carry.geo.domain.model.GeocodingResult
import com.carry.geo.domain.vo.AddressComponent
import com.carry.geo.domain.vo.Coordinate
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverGeocodingResponse(
    val status: String,
    val meta: Meta,
    val addresses: List<Address>,
    val errorMessage: String?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Meta(
        val totalCount: Int,
        val page: Int,
        val count: Int,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Address(
        val roadAddress: String,
        val jibunAddress: String,
        val englishAddress: String?,
        val addressElements: List<AddressElement>,
        val x: String,
        val y: String,
        val distance: Double?,
    ) {
        fun toDomain(): GeocodingResult {
            val elementMap = addressElements.associateBy { it.resolveType() }

            return GeocodingResult(
                jibunAddress = jibunAddress,
                roadAddress = roadAddress,
                coordinate = Coordinate(
                    latitude = y.toDouble(),
                    longitude = x.toDouble(),
                ),
                addressComponent = AddressComponent(
                    sido = elementMap[AddressType.SIDO]?.longNameOrNull(),
                    sigungu = elementMap[AddressType.SIGUGUN]?.longNameOrNull(),
                    dongmyun = elementMap[AddressType.DONGMYUN]?.longNameOrNull(),
                    ri = elementMap[AddressType.RI]?.longNameOrNull(),
                    roadName = elementMap[AddressType.ROAD_NAME]?.longNameOrNull(),
                    buildingName = elementMap[AddressType.BUILDING_NAME]?.longNameOrNull(),
                    landNumber = elementMap[AddressType.LAND_NUMBER]?.longNameOrNull(),
                    postalCode = elementMap[AddressType.POSTAL_CODE]?.longNameOrNull(),
                ),
            )
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AddressElement(
        val types: List<String>,
        val longName: String,
        val shortName: String,
        val code: String,
    ) {
        fun resolveType(): AddressType? =
            types.firstNotNullOfOrNull { AddressType.fromValue(it) }

        fun longNameOrNull(): String? = longName.ifBlank { null }
    }

    enum class AddressType(val value: String) {
        SIDO("SIDO"),
        SIGUGUN("SIGUGUN"),
        DONGMYUN("DONGMYUN"),
        RI("RI"),
        ROAD_NAME("ROAD_NAME"),
        BUILDING_NUMBER("BUILDING_NUMBER"),
        BUILDING_NAME("BUILDING_NAME"),
        LAND_NUMBER("LAND_NUMBER"),
        POSTAL_CODE("POSTAL_CODE"),
        ;

        companion object {
            private val map = entries.associateBy { it.value }
            fun fromValue(value: String): AddressType? = map[value]
        }
    }
}
