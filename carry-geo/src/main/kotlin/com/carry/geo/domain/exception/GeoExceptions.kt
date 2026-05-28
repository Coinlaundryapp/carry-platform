package com.carry.geo.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode

class GeocodingFailedException(message: String, cause: Throwable? = null) : BusinessException(
    ErrorCode.GEOCODING_FAILED,
    message,
    cause,
)

class ReverseGeocodingFailedException(message: String, cause: Throwable? = null) : BusinessException(
    ErrorCode.REVERSE_GEOCODING_FAILED,
    message,
    cause,
)

class GeocodingResultEmptyException(address: String) : BusinessException(
    ErrorCode.ADDRESS_NOT_FOUND,
    "주소에 대한 지오코딩 결과가 없습니다: $address",
)
