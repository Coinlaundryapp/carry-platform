package com.carry.notification.domain.vo

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode

enum class DevicePlatform {
    WEB,
    ;

    companion object {
        /** 와이어 문자열("web") → enum. 대소문자 무시. 미지원 값은 예외. */
        fun fromWire(value: String): DevicePlatform =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw BusinessException(ErrorCode.INVALID_DEVICE_PLATFORM, "지원하지 않는 platform: $value")
    }
}
