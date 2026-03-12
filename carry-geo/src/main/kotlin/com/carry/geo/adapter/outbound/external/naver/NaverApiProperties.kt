package com.carry.geo.adapter.outbound.external.naver

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "carry.geo.naver")
data class NaverApiProperties(
    val clientId: String,
    val clientSecret: String,
    val geocodingUrl: String = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode",
    val reverseGeocodingUrl: String = "https://naveropenapi.apigw.ntruss.com/map-reversegeocode/v2/gc",
)
