package com.carry.geo.domain.exception

class GeocodingFailedException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class ReverseGeocodingFailedException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class GeocodingResultEmptyException(address: String) :
    RuntimeException("주소에 대한 지오코딩 결과가 없습니다: $address")
